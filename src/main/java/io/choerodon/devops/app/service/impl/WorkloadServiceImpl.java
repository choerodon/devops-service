package io.choerodon.devops.app.service.impl;

import java.io.IOException;
import java.util.*;

import com.google.common.collect.Iterables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.vo.WorkloadBaseCreateOrUpdateVO;
import io.choerodon.devops.api.vo.WorkloadBaseVO;
import io.choerodon.devops.app.service.*;
import io.choerodon.devops.infra.dto.DevopsDeploymentDTO;
import io.choerodon.devops.infra.dto.DevopsEnvCommandDTO;
import io.choerodon.devops.infra.dto.DevopsEnvironmentDTO;
import io.choerodon.devops.infra.dto.UserAttrDTO;
import io.choerodon.devops.infra.enums.CommandStatus;
import io.choerodon.devops.infra.enums.CommandType;
import io.choerodon.devops.infra.enums.ObjectType;
import io.choerodon.devops.infra.enums.ResourceType;
import io.choerodon.devops.infra.feign.operator.GitlabServiceClientOperator;
import io.choerodon.devops.infra.gitops.ResourceConvertToYamlHandler;
import io.choerodon.devops.infra.handler.ClusterConnectionHandler;
import io.choerodon.devops.infra.util.*;

@Service
public class WorkloadServiceImpl implements WorkloadService {

    public static final String CREATE = "create";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";
    public static final String METADATA = "metadata";
    public static final String KIND = "kind";

    private static final Map<String, String> RESOURCE_FILE_TEMPLATE_PATH_MAP;

    @Autowired
    private GitlabServiceClientOperator gitlabServiceClientOperator;

    @Autowired
    private PermissionHelper permissionHelper;

    @Autowired
    private UserAttrService userAttrService;

    @Autowired
    private DevopsEnvironmentService devopsEnvironmentService;

    @Autowired
    private ClusterConnectionHandler clusterConnectionHandler;

    @Autowired
    private DevopsEnvCommandService devopsEnvCommandService;

    @Autowired
    private DevopsDeploymentService devopsDeploymentService;

    @Autowired
    private DevopsWorkloadResourceContentService devopsWorkloadResourceContentService;

    private static final List<String> RESOURCE_TYPE = Arrays.asList(
            ResourceType.DEPLOYMENT.getType(),
            ResourceType.JOB.getType(),
            ResourceType.CRON_JOB.getType(),
            ResourceType.DAEMONSET.getType(),
            ResourceType.STATEFULSET.getType()
    );

    static {
        Map<String, String> filePathMap = new HashMap<>();
        filePathMap.put(ResourceType.DEPLOYMENT.getType(), "deploy-%s.yaml");
        filePathMap.put(ResourceType.JOB.getType(), "job-%s.yaml");
        filePathMap.put(ResourceType.CRON_JOB.getType(), "cj-%s.yaml");
        filePathMap.put(ResourceType.DAEMONSET.getType(), "ds-%s.yaml");
        filePathMap.put(ResourceType.STATEFULSET.getType(), "sts-%s.yaml");
        RESOURCE_FILE_TEMPLATE_PATH_MAP = Collections.unmodifiableMap(filePathMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrUpdate(Long projectId, WorkloadBaseCreateOrUpdateVO workloadBaseCreateOrUpdateVO, MultipartFile multipartFile, ResourceType resourceType) {
        WorkloadBaseVO workloadBaseVO = processKeyEncrypt(workloadBaseCreateOrUpdateVO);
        DevopsEnvironmentDTO devopsEnvironmentDTO = permissionHelper.checkEnvBelongToProject(projectId, workloadBaseVO.getEnvId());

        UserAttrDTO userAttrDTO = userAttrService.baseQueryById(TypeUtil.objToLong(GitUserNameUtil.getUserId()));

        //校验环境相关信息
        devopsEnvironmentService.checkEnv(devopsEnvironmentDTO, userAttrDTO);

        String resourceFilePath = "";

        String content = workloadBaseVO.getContent();
        if (multipartFile != null) {
            try {
                content = new String(multipartFile.getBytes());
            } catch (IOException e) {
                throw new CommonException("error.read.multipart.file");
            }
        }

        Yaml yaml = new Yaml();
        List<Object> objects = new ArrayList<>();

        //处理每个k8s资源对象
        Iterable<Object> workLoads = yaml.loadAll(content);
        int size = Iterables.size(workLoads);
        if (size != 1) {
            throw new CommonException("error.workload.size", resourceType.getType());
        }

        try {
            for (Object data : yaml.loadAll(content)) {
                Map<String, Object> datas = (Map<String, Object>) data;

                String kind = (String) datas.get(KIND);

                LinkedHashMap metadata = (LinkedHashMap) datas.get(METADATA);
                //校验yaml文件内资源属性是否合法,并返回资源的name
                String name = checkResource(metadata, kind, resourceType.getType());

                // 前面对资源进行了数量校验，因此只会循环一次，resourceFilePath也只会被设置一次
                resourceFilePath = String.format(RESOURCE_FILE_TEMPLATE_PATH_MAP.get(resourceType.getType()), name);
                objects.add(datas);
                handleWorkLoad(projectId, workloadBaseVO.getEnvId(), FileUtil.getYaml().dump(data), kind, name, workloadBaseVO.getOperateType(), workloadBaseVO.getResourceId(), null);
            }
        } catch (CommonException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CommonException("error.load.yaml.content");
        }

        if (CREATE.equals(workloadBaseVO.getOperateType())) {
            gitlabServiceClientOperator.createFile(devopsEnvironmentDTO.getGitlabEnvProjectId().intValue(), resourceFilePath, FileUtil.getYaml().dumpAll(objects.iterator()),
                    "ADD FILE", TypeUtil.objToInteger(userAttrDTO.getGitlabUserId()));
        } else {
            //判断当前容器目录下是否存在环境对应的gitops文件目录，不存在则克隆
            String gitOpsPath = clusterConnectionHandler.handDevopsEnvGitRepository(
                    devopsEnvironmentDTO.getProjectId(),
                    devopsEnvironmentDTO.getCode(),
                    devopsEnvironmentDTO.getId(),
                    devopsEnvironmentDTO.getEnvIdRsa(),
                    devopsEnvironmentDTO.getType(),
                    devopsEnvironmentDTO.getClusterCode());

            if (!gitlabServiceClientOperator.getFile(TypeUtil.objToInteger(devopsEnvironmentDTO.getGitlabEnvProjectId()), "master",
                    resourceFilePath)) {
                throw new CommonException("error.fileResource.not.exist");
            }
            //获取更新内容
            ResourceConvertToYamlHandler<Object> resourceConvertToYamlHandler = new ResourceConvertToYamlHandler<>();
            // TODO 这里的get(0)似乎意味着不支持多个资源的更新
            String updateContent = resourceConvertToYamlHandler.getUpdateContent(objects.get(0), false, null, resourceFilePath, resourceType.getType(), gitOpsPath, CommandType.UPDATE.getType());
            gitlabServiceClientOperator.updateFile(devopsEnvironmentDTO.getGitlabEnvProjectId().intValue(), resourceFilePath, updateContent, "UPDATE FILE", TypeUtil.objToInteger(userAttrDTO.getGitlabUserId()));
        }
    }

    private WorkloadBaseVO processKeyEncrypt(WorkloadBaseCreateOrUpdateVO workloadBaseCreateOrUpdateVO) {
        // TODO 待hzero兼容 ModelAttribute 注解后删除
        WorkloadBaseVO decryptedWorkloadBaseVO = ConvertUtils.convertObject(workloadBaseCreateOrUpdateVO, WorkloadBaseVO.class);
        decryptedWorkloadBaseVO.setEnvId(KeyDecryptHelper.decryptValue(workloadBaseCreateOrUpdateVO.getEnvId()));
        decryptedWorkloadBaseVO.setResourceId(KeyDecryptHelper.decryptValue(workloadBaseCreateOrUpdateVO.getResourceId()));
        return decryptedWorkloadBaseVO;
    }

    private void handleWorkLoad(Long projectId, Long envId, String content, String kind, String name, String operateType, Long resourceId, Long userId) {
        if (CREATE.equals(operateType)) {
            createWorkload(projectId, envId, content, kind, operateType, name, userId);
        } else if (UPDATE.equals(operateType)) {
            updateWorkLoad(kind, name, content, resourceId, userId);
        }
    }


    private String checkResource(LinkedHashMap metadata, String kind, String resourceType) {
        if (kind == null) {
            throw new CommonException("error.workload.resource.kind.not.found");
        }
        // 禁止创建不同资源
        if (!resourceType.equals(kind)) {
            throw new CommonException("error.workload.resource.kind.mismatch");
        }
        if (metadata == null) {
            throw new CommonException("error.workload.resource.metadata.not.found");
        }

        Object name = metadata.get("name");
        if (StringUtils.isEmpty(name)) {
            throw new CommonException("error.workload.resource.name.not.found");
        }
        return name.toString();
    }

    private DevopsEnvCommandDTO initDevopsEnvCommandDTO(String type, Long userId) {
        DevopsEnvCommandDTO devopsEnvCommandDTO = new DevopsEnvCommandDTO();
        switch (type) {
            case CREATE:
                devopsEnvCommandDTO.setCommandType(CommandType.CREATE.getType());
                break;
            case UPDATE:
                devopsEnvCommandDTO.setCommandType(CommandType.UPDATE.getType());
                break;
            default:
                devopsEnvCommandDTO.setCommandType(CommandType.DELETE.getType());
                break;
        }
        devopsEnvCommandDTO.setCreatedBy(userId);
        devopsEnvCommandDTO.setObject(ObjectType.CUSTOM.getType());
        devopsEnvCommandDTO.setStatus(CommandStatus.OPERATING.getStatus());
        return devopsEnvCommandDTO;
    }

    private void checkWorkloadExist(String type, Long envId, String name) {
        switch (type) {
            case "Deployment":
                devopsDeploymentService.checkExist(envId, name);
                break;
        }
    }

    private void createWorkload(Long projectId, Long envId, String content, String type, String operateType, String name, Long userId) {
        //校验新增的类型是否已存在
        checkWorkloadExist(type, envId, name);

        //自定义资源关联command
        DevopsEnvCommandDTO devopsEnvCommandDTO = initDevopsEnvCommandDTO(operateType, userId);
        devopsEnvCommandDTO = devopsEnvCommandService.baseCreate(devopsEnvCommandDTO);
        Long workLoadId = null;

        switch (type) {
            case "Deployment":
                DevopsDeploymentDTO devopsDeploymentDTO = new DevopsDeploymentDTO(name, projectId, envId, devopsEnvCommandDTO.getId());
                workLoadId = devopsDeploymentService.baseCreate(devopsDeploymentDTO);
                break;
        }
        devopsWorkloadResourceContentService.create(type, workLoadId, content);

        devopsEnvCommandDTO.setObjectId(workLoadId);
        devopsEnvCommandService.baseUpdate(devopsEnvCommandDTO);
    }


    private void updateWorkLoad(String type, String name, String content, Long resourceId, Long userId) {
        //自定义资源关联command
        DevopsEnvCommandDTO devopsEnvCommandDTO = initDevopsEnvCommandDTO(type, userId);
        switch (type) {
            case "Deployment":
                updateDeployment(devopsEnvCommandDTO, name, resourceId);
                break;
        }
        devopsWorkloadResourceContentService.update(type, resourceId, content);
    }

    private void checkMetadataInfo(String nowName, String oldName) {
        if (!nowName.equals(oldName)) {
            throw new CommonException("error.workload.resource.name.modify");
        }
    }

    private void updateDeployment(DevopsEnvCommandDTO devopsEnvCommandDTO, String newName, Long resourceId) {
        DevopsDeploymentDTO devopsDeploymentDTO = (DevopsDeploymentDTO) devopsDeploymentService.selectByPrimaryKey(resourceId);
        checkMetadataInfo(newName, devopsDeploymentDTO.getName());
        devopsEnvCommandDTO.setObjectId(devopsDeploymentDTO.getId());
        devopsEnvCommandDTO = devopsEnvCommandService.baseCreate(devopsEnvCommandDTO);

        //更新资源关联的最新command
        devopsDeploymentDTO.setCommandId(devopsEnvCommandDTO.getId());
        devopsDeploymentService.baseUpdate(devopsDeploymentDTO);
    }
}

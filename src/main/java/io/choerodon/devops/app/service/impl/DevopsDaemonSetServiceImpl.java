package io.choerodon.devops.app.service.impl;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.kubernetes.client.JSON;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1beta2DaemonSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.utils.ConvertUtils;
import io.choerodon.devops.api.vo.DaemonSetInfoVO;
import io.choerodon.devops.api.vo.DevopsDaemonSetVO;
import io.choerodon.devops.app.service.DevopsDaemonSetService;
import io.choerodon.devops.app.service.DevopsEnvResourceDetailService;
import io.choerodon.devops.app.service.DevopsWorkloadResourceContentService;
import io.choerodon.devops.infra.dto.DevopsDaemonSetDTO;
import io.choerodon.devops.infra.dto.DevopsEnvResourceDetailDTO;
import io.choerodon.devops.infra.enums.ResourceType;
import io.choerodon.devops.infra.mapper.DevopsDaemonSetMapper;
import io.choerodon.devops.infra.util.MapperUtil;
import io.choerodon.devops.infra.util.TypeUtil;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;

/**
 * 〈功能简述〉
 * 〈〉
 *
 * @author wanghao
 * @since 2021/6/8 11:19
 */
@Service
public class DevopsDaemonSetServiceImpl implements DevopsDaemonSetService {
    @Autowired
    private DevopsDaemonSetMapper devopsDaemonSetMapper;
    private final JSON json = new JSON();
    @Autowired
    private DevopsEnvResourceDetailService devopsEnvResourceDetailService;
    @Autowired
    private DevopsWorkloadResourceContentService devopsWorkloadResourceContentService;

    @Override
    public Page<DaemonSetInfoVO> pagingByEnvId(Long projectId, Long envId, PageRequest pageable, String name, Boolean fromInstance) {
        Page<DevopsDaemonSetVO> devopsDaemonSetVOPage = PageHelper.doPage(pageable, () -> devopsDaemonSetMapper.listByEnvId(envId, name, fromInstance));
        Page<DaemonSetInfoVO> daemonSetInfoVOPage = new Page<>();
        if (CollectionUtils.isEmpty(devopsDaemonSetVOPage.getContent())) {
            return daemonSetInfoVOPage;
        }
        Set<Long> detailsIds = devopsDaemonSetVOPage.getContent().stream().map(DevopsDaemonSetVO::getResourceDetailId)
                .collect(Collectors.toSet());
        List<DevopsEnvResourceDetailDTO> devopsEnvResourceDetailDTOS = devopsEnvResourceDetailService.listByMessageIds(detailsIds);
        Map<Long, DevopsEnvResourceDetailDTO> detailDTOMap = devopsEnvResourceDetailDTOS.stream().collect(Collectors.toMap(DevopsEnvResourceDetailDTO::getId, Function.identity()));

        return ConvertUtils.convertPage(devopsDaemonSetVOPage, v -> {
            DaemonSetInfoVO daemonSetInfoVO = ConvertUtils.convertObject(v, DaemonSetInfoVO.class);
            if (detailDTOMap.get(v.getResourceDetailId()) != null) {
                // 参考实例详情查询逻辑
                V1beta2DaemonSet v1beta2DaemonSet = json.deserialize(detailDTOMap.get(v.getResourceDetailId()).getMessage(), V1beta2DaemonSet.class);


                daemonSetInfoVO.setName(v1beta2DaemonSet.getMetadata().getName());
                daemonSetInfoVO.setAge(v1beta2DaemonSet.getMetadata().getCreationTimestamp().toString());
                daemonSetInfoVO.setCurrentScheduled(TypeUtil.objToLong(v1beta2DaemonSet.getStatus().getCurrentNumberScheduled()));
                daemonSetInfoVO.setDesiredScheduled(TypeUtil.objToLong(v1beta2DaemonSet.getStatus().getDesiredNumberScheduled()));
                daemonSetInfoVO.setNumberAvailable(TypeUtil.objToLong(v1beta2DaemonSet.getStatus().getNumberAvailable()));

                daemonSetInfoVO.setLabels(v1beta2DaemonSet.getSpec().getSelector().getMatchLabels());
                List<Integer> portRes = new ArrayList<>();
                for (V1Container container : v1beta2DaemonSet.getSpec().getTemplate().getSpec().getContainers()) {
                    List<V1ContainerPort> ports = container.getPorts();
                    Optional.ofNullable(ports).ifPresent(portList -> {
                        for (V1ContainerPort port : portList) {
                            portRes.add(port.getContainerPort());
                        }
                    });
                }
                daemonSetInfoVO.setPorts(portRes);
                daemonSetInfoVO.setAge(v.getLastUpdateDate().toString());
            }
            return daemonSetInfoVO;
        });
    }


    @Override
    public DevopsDaemonSetDTO selectByPrimaryKey(Long resourceId) {
        return devopsDaemonSetMapper.selectByPrimaryKey(resourceId);
    }

    @Override
    public void checkExist(Long envId, String name) {
        if (devopsDaemonSetMapper.selectCountByEnvIdAndName(envId, name) != 0) {
            throw new CommonException("error.workload.exist", "DaemonSet", name);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long baseCreate(DevopsDaemonSetDTO devopsDaemonSetDTO) {
        devopsDaemonSetMapper.insert(devopsDaemonSetDTO);
        return devopsDaemonSetDTO.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void baseUpdate(DevopsDaemonSetDTO devopsDaemonSetDTOToUpdate) {
        if (devopsDaemonSetDTOToUpdate.getObjectVersionNumber() == null) {
            DevopsDaemonSetDTO devopsDaemonSetDTO = devopsDaemonSetMapper.selectByPrimaryKey(devopsDaemonSetDTOToUpdate.getId());
            devopsDaemonSetDTOToUpdate.setObjectVersionNumber(devopsDaemonSetDTO.getObjectVersionNumber());
        }
        MapperUtil.resultJudgedUpdateByPrimaryKeySelective(devopsDaemonSetMapper, devopsDaemonSetDTOToUpdate, "error.daemonset.update");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void baseDelete(Long id) {
        devopsDaemonSetMapper.deleteByPrimaryKey(id);
        devopsWorkloadResourceContentService.deleteByResourceId(ResourceType.DEPLOYMENT.getType(), id);
    }

    @Override
    public DevopsDaemonSetDTO baseQueryByEnvIdAndName(Long envId, String name) {
        DevopsDaemonSetDTO devopsDaemonSetDTO = new DevopsDaemonSetDTO();
        devopsDaemonSetDTO.setEnvId(envId);
        devopsDaemonSetDTO.setName(name);
        return devopsDaemonSetMapper.selectOne(devopsDaemonSetDTO);
    }

}

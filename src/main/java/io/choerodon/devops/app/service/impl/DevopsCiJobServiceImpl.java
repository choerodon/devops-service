package io.choerodon.devops.app.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.choerodon.devops.api.vo.CiCdPipelineVO;
import io.choerodon.devops.infra.dto.gitlab.BranchDTO;
import io.choerodon.devops.infra.dto.gitlab.GitLabUserDTO;
import io.choerodon.devops.infra.dto.gitlab.MemberDTO;
import io.choerodon.devops.infra.enums.AccessLevel;
import org.hzero.boot.file.FileClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import retrofit2.Response;

import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.vo.SonarInfoVO;
import io.choerodon.devops.api.vo.SonarQubeConfigVO;
import io.choerodon.devops.app.service.*;
import io.choerodon.devops.infra.dto.*;
import io.choerodon.devops.infra.dto.gitlab.JobDTO;
import io.choerodon.devops.infra.enums.AppServiceEvent;
import io.choerodon.devops.infra.enums.JobTypeEnum;
import io.choerodon.devops.infra.enums.sonar.SonarAuthType;
import io.choerodon.devops.infra.exception.DevopsCiInvalidException;
import io.choerodon.devops.infra.feign.SonarClient;
import io.choerodon.devops.infra.feign.operator.BaseServiceClientOperator;
import io.choerodon.devops.infra.feign.operator.GitlabServiceClientOperator;
import io.choerodon.devops.infra.handler.RetrofitHandler;
import io.choerodon.devops.infra.mapper.*;
import io.choerodon.devops.infra.util.GitUserNameUtil;
import io.choerodon.devops.infra.util.JsonHelper;
import io.choerodon.devops.infra.util.TypeUtil;

/**
 * 〈功能简述〉
 * 〈〉
 *
 * @author wanghao
 * @since 2020/4/3 9:24
 */
@Service
public class DevopsCiJobServiceImpl implements DevopsCiJobService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevopsCiJobServiceImpl.class);

    private static final String CREATE_JOB_FAILED = "create.job.failed";
    private static final String ERROR_STAGE_ID_IS_NULL = "error.stage.id.is.null";
    private static final String ERROR_PIPELINE_ID_IS_NULL = "error.pipeline.id.is.null";
    private static final String ERROR_GITLAB_PROJECT_ID_IS_NULL = "error.gitlab.project.id.is.null";
    private static final String ERROR_GITLAB_JOB_ID_IS_NULL = "error.gitlab.job.id.is.null";
    private static final String ERROR_TOKEN_MISMATCH = "error.app.service.token.mismatch";
    private static final String ERROR_CI_JOB_NON_EXIST = "error.ci.job.non.exist";
    private static final String ERROR_TOKEN_PIPELINE_MISMATCH = "error.app.service.token.pipeline.mismatch";

    private static final String SONAR = "sonar";

    private DevopsCiJobMapper devopsCiJobMapper;
    private GitlabServiceClientOperator gitlabServiceClientOperator;
    private UserAttrService userAttrService;
    private DevopsCiMavenSettingsMapper devopsCiMavenSettingsMapper;
    private AppServiceService appServiceService;
    private DevopsCiCdPipelineMapper devopsCiCdPipelineMapper;
    private DevopsCiPipelineService devopsCiPipelineService;
    private DevopsCiJobRecordService devopsCiJobRecordService;
    private DevopsCiPipelineRecordMapper devopsCiPipelineRecordMapper;
    private AppServiceMapper appServiceMapper;
    private CheckGitlabAccessLevelService checkGitlabAccessLevelService;
    private DevopsCiJobRecordMapper devopsCiJobRecordMapper;

    public DevopsCiJobServiceImpl(DevopsCiJobMapper devopsCiJobMapper,
                                  GitlabServiceClientOperator gitlabServiceClientOperator,
                                  UserAttrService userAttrService,
                                  AppServiceService appServiceService,
                                  DevopsCiCdPipelineMapper devopsCiCdPipelineMapper,
                                  DevopsCiMavenSettingsMapper devopsCiMavenSettingsMapper,
                                  @Lazy DevopsCiPipelineService devopsCiPipelineService,
                                  DevopsCiJobRecordService devopsCiJobRecordService,
                                  FileClient fileClient,
                                  AppServiceMapper appServiceMapper,
                                  CheckGitlabAccessLevelService checkGitlabAccessLevelService,
                                  BaseServiceClientOperator baseServiceClientOperator,
                                  DevopsCiPipelineRecordMapper devopsCiPipelineRecordMapper,
                                  DevopsCiJobRecordMapper devopsCiJobRecordMapper) {
        this.devopsCiJobMapper = devopsCiJobMapper;
        this.gitlabServiceClientOperator = gitlabServiceClientOperator;
        this.userAttrService = userAttrService;
        this.devopsCiMavenSettingsMapper = devopsCiMavenSettingsMapper;
        this.appServiceService = appServiceService;
        this.devopsCiCdPipelineMapper = devopsCiCdPipelineMapper;
        this.devopsCiPipelineService = devopsCiPipelineService;
        this.devopsCiJobRecordService = devopsCiJobRecordService;
        this.devopsCiPipelineRecordMapper = devopsCiPipelineRecordMapper;
        this.appServiceMapper = appServiceMapper;
        this.checkGitlabAccessLevelService = checkGitlabAccessLevelService;
        this.devopsCiJobRecordMapper = devopsCiJobRecordMapper;
    }

    @Override
    @Transactional
    public DevopsCiJobDTO create(DevopsCiJobDTO devopsCiJobDTO) {
        devopsCiJobDTO.setId(null);
        if (devopsCiJobMapper.insertSelective(devopsCiJobDTO) != 1) {
            throw new CommonException(CREATE_JOB_FAILED);
        }
        return devopsCiJobMapper.selectByPrimaryKey(devopsCiJobDTO.getId());
    }

    @Override
    @Transactional
    public void deleteByStageId(Long stageId) {
        if (stageId == null) {
            throw new CommonException(ERROR_STAGE_ID_IS_NULL);
        }

        List<Long> jobIds = listByStageId(stageId).stream().map(DevopsCiJobDTO::getId).collect(Collectors.toList());
        if (!jobIds.isEmpty()) {
            DevopsCiJobDTO devopsCiJobDTO = new DevopsCiJobDTO();
            devopsCiJobDTO.setCiStageId(stageId);
            devopsCiJobMapper.delete(devopsCiJobDTO);
        }
    }

    @Override
    public List<DevopsCiJobDTO> listByPipelineId(Long ciPipelineId) {
        if (ciPipelineId == null) {
            throw new CommonException(ERROR_PIPELINE_ID_IS_NULL);
        }
        DevopsCiJobDTO devopsCiJobDTO = new DevopsCiJobDTO();
        devopsCiJobDTO.setCiPipelineId(ciPipelineId);
        return devopsCiJobMapper.select(devopsCiJobDTO);
    }

    @Override
    public List<DevopsCiJobDTO> listByStageId(Long stageId) {
        DevopsCiJobDTO devopsCiJobDTO = new DevopsCiJobDTO();
        devopsCiJobDTO.setCiStageId(Objects.requireNonNull(stageId));
        return devopsCiJobMapper.select(devopsCiJobDTO);
    }

    @Override
    public Boolean sonarConnect(Long projectId, SonarQubeConfigVO sonarQubeConfigVO) {
        if (Objects.isNull(sonarQubeConfigVO)) {
            return false;
        }
        if (SonarAuthType.USERNAME_PWD.value().equals(sonarQubeConfigVO.getAuthType())) {
            try {
                SonarClient sonarClient = RetrofitHandler.getSonarClient(
                        sonarQubeConfigVO.getSonarUrl(),
                        SONAR,
                        sonarQubeConfigVO.getUsername(),
                        sonarQubeConfigVO.getPassword());

                Response<Void> execute = sonarClient.getUser().execute();
                if (!Objects.isNull(execute.errorBody())) {
                    LOGGER.error("test connect response code :{},error messsage:{}", execute.code(), execute.errorBody().toString());
                    return false;
                } else {
                    return true;
                }
            } catch (Exception e) {
                LOGGER.error("error connect :", e);
                return false;
            }
        }
        return true;
    }

    @Override
    public String queryTrace(Long gitlabProjectId, Long jobId) {
        UserAttrDTO userAttrDTO = userAttrService.baseQueryById(GitUserNameUtil.getUserId());
        //检查该用户是否有git库的权限
        AppServiceDTO appServiceDTO = appServiceMapper.selectOne(new AppServiceDTO().setGitlabProjectId(TypeUtil.objToInteger(gitlabProjectId)));
        checkGitlabAccessLevelService.checkGitlabPermission(appServiceDTO.getProjectId(), appServiceDTO.getId(), AppServiceEvent.CI_PIPELINE_DETAIL);
        return gitlabServiceClientOperator.queryTrace(gitlabProjectId.intValue(), jobId.intValue(), userAttrDTO.getGitlabUserId().intValue());
    }

    @Override
    public void retryJob(Long projectId, Long gitlabProjectId, Long jobId) {
        Assert.notNull(gitlabProjectId, ERROR_GITLAB_PROJECT_ID_IS_NULL);
        Assert.notNull(jobId, ERROR_GITLAB_JOB_ID_IS_NULL);
        AppServiceDTO appServiceDTO = appServiceMapper.selectOne(new AppServiceDTO().setGitlabProjectId(TypeUtil.objToInteger(gitlabProjectId)));
        checkGitlabAccessLevelService.checkGitlabPermission(projectId, appServiceDTO.getId(), AppServiceEvent.CI_PIPELINE_RETRY_TASK);

        UserAttrDTO userAttrDTO = userAttrService.baseQueryById(GitUserNameUtil.getUserId());
        DevopsCiJobRecordDTO devopsCiJobRecordDTO = devopsCiJobRecordService.queryByGitlabJobId(jobId);
        DevopsCiPipelineRecordDTO devopsCiPipelineRecordDTO = devopsCiPipelineRecordMapper.selectByPrimaryKey(devopsCiJobRecordDTO.getCiPipelineRecordId());
        devopsCiPipelineService.checkUserBranchPushPermission(projectId, userAttrDTO.getGitlabUserId(), gitlabProjectId, devopsCiPipelineRecordDTO.getGitlabTriggerRef());

        JobDTO jobDTO = gitlabServiceClientOperator.retryJob(gitlabProjectId.intValue(), jobId.intValue(), userAttrDTO.getGitlabUserId().intValue());
        // 保存job记录
        try {
            devopsCiJobRecordService.create(devopsCiPipelineRecordDTO.getId(), gitlabProjectId, jobDTO, userAttrDTO.getIamUserId());
        } catch (Exception e) {
            LOGGER.info("update job Records failed， jobid {}.", jobId);
        }
    }

    @Override
    @Transactional
    public void deleteByPipelineId(Long ciPipelineId) {
        if (ciPipelineId == null) {
            throw new CommonException(ERROR_PIPELINE_ID_IS_NULL);
        }

        // 删除maven settings
        deleteMavenSettingsRecordByJobIds(listByPipelineId(ciPipelineId).stream().map(DevopsCiJobDTO::getId).collect(Collectors.toList()));

        DevopsCiJobDTO devopsCiJobDTO = new DevopsCiJobDTO();
        devopsCiJobDTO.setCiPipelineId(ciPipelineId);
        devopsCiJobMapper.delete(devopsCiJobDTO);
    }

    @Override
    public String queryMavenSettings(Long projectId, String appServiceToken, Long jobId, Long sequence) {
        AppServiceDTO appServiceDTO = appServiceService.baseQueryByToken(appServiceToken);
        if (appServiceDTO == null) {
            throw new DevopsCiInvalidException(ERROR_TOKEN_MISMATCH);
        }
        DevopsCiJobDTO devopsCiJobDTO = devopsCiJobMapper.selectByPrimaryKey(jobId);
        if (devopsCiJobDTO == null) {
            throw new DevopsCiInvalidException(ERROR_CI_JOB_NON_EXIST);
        }

        CiCdPipelineDTO devopsCiPipelineDTO = devopsCiCdPipelineMapper.selectByPrimaryKey(devopsCiJobDTO.getCiPipelineId());
        if (devopsCiPipelineDTO == null || !Objects.equals(devopsCiPipelineDTO.getAppServiceId(), appServiceDTO.getId())) {
            throw new DevopsCiInvalidException(ERROR_TOKEN_PIPELINE_MISMATCH);
        }

        return devopsCiMavenSettingsMapper.queryMavenSettings(jobId, sequence);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteMavenSettingsRecordByJobIds(List<Long> jobIds) {
        if (CollectionUtils.isEmpty(jobIds)) {
            return;
        }
        devopsCiMavenSettingsMapper.deleteByJobIds(jobIds);
    }

    @Override
    public SonarInfoVO getSonarConfig(Long projectId, Long appServiceId, String code) {
        SonarInfoVO sonarInfoVO = new SonarInfoVO();
        if (!Objects.isNull(appServiceId)) {
            sonarInfoVO = getCiSonar(appServiceId);
        }
        if (!Objects.isNull(code)) {
            AppServiceDTO appServiceDTO = new AppServiceDTO();
            appServiceDTO.setCode(code);
            AppServiceDTO serviceDTO = appServiceMapper.selectOne(appServiceDTO);
            if (!Objects.isNull(serviceDTO)) {
                sonarInfoVO = getCiSonar(serviceDTO.getId());
            }
        }
        return sonarInfoVO;
    }

    @Override
    @Transactional
    public void playJob(Long projectId, Long gitlabProjectId, Long jobId) {
        Assert.notNull(gitlabProjectId, ERROR_GITLAB_PROJECT_ID_IS_NULL);
        Assert.notNull(jobId, ERROR_GITLAB_JOB_ID_IS_NULL);

        UserAttrDTO userAttrDTO = userAttrService.baseQueryById(GitUserNameUtil.getUserId());
        DevopsCiJobRecordDTO devopsCiJobRecordDTO = devopsCiJobRecordService.queryByGitlabJobId(jobId);
        DevopsCiPipelineRecordDTO devopsCiPipelineRecordDTO = devopsCiPipelineRecordMapper.selectByPrimaryKey(devopsCiJobRecordDTO.getCiPipelineRecordId());

        devopsCiPipelineService.checkUserBranchMergePermission(projectId, userAttrDTO.getGitlabUserId(), gitlabProjectId, devopsCiPipelineRecordDTO.getGitlabTriggerRef());


        JobDTO jobDTO = gitlabServiceClientOperator.playJob(gitlabProjectId.intValue(), jobId.intValue(), userAttrDTO.getGitlabUserId().intValue());

        devopsCiJobRecordDTO.setStatus(jobDTO.getStatus().toString());

        devopsCiJobRecordMapper.updateByPrimaryKeySelective(devopsCiJobRecordDTO);
    }

    private SonarInfoVO getCiSonar(Long appServiceId) {
        SonarInfoVO sonarInfoVO = new SonarInfoVO();
        CiCdPipelineDTO devopsCiPipelineDTO = new CiCdPipelineDTO();
        devopsCiPipelineDTO.setAppServiceId(appServiceId);
        CiCdPipelineDTO ciPipelineDTO = devopsCiCdPipelineMapper.selectOne(devopsCiPipelineDTO);
        if (!Objects.isNull(ciPipelineDTO)) {
            DevopsCiJobDTO devopsCiJobDTO = new DevopsCiJobDTO();
            devopsCiJobDTO.setCiPipelineId(ciPipelineDTO.getId());
            devopsCiJobDTO.setType(JobTypeEnum.SONAR.value());
            DevopsCiJobDTO ciJobDTO = devopsCiJobMapper.selectOne(devopsCiJobDTO);
            if (!Objects.isNull(ciJobDTO) && !StringUtils.isEmpty(ciJobDTO.getMetadata())) {
                sonarInfoVO = JsonHelper.unmarshalByJackson(ciJobDTO.getMetadata(), SonarInfoVO.class);
            }
        }
        return sonarInfoVO;
    }
}

package io.choerodon.devops.app.eventhandler.payload;

import io.choerodon.devops.api.vo.MiddlewareRedisHostDeployVO;
import io.choerodon.devops.api.vo.deploy.DeploySourceVO;

public class DevopsMiddlewareRedisDeployPayload {
    private Long projectId;
    private Long deployRecordId;
    private MiddlewareRedisHostDeployVO middlewareRedisHostDeployVO;
    private DeploySourceVO deploySourceVO;

    public Long getDeployRecordId() {
        return deployRecordId;
    }

    public void setDeployRecordId(Long deployRecordId) {
        this.deployRecordId = deployRecordId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public MiddlewareRedisHostDeployVO getMiddlewareRedisHostDeployVO() {
        return middlewareRedisHostDeployVO;
    }

    public void setMiddlewareRedisHostDeployVO(MiddlewareRedisHostDeployVO middlewareRedisHostDeployVO) {
        this.middlewareRedisHostDeployVO = middlewareRedisHostDeployVO;
    }

    public void setDeploySourceVO(DeploySourceVO deploySourceVO) {
        this.deploySourceVO = deploySourceVO;
    }

    public DeploySourceVO getDeploySourceVO() {
        return deploySourceVO;
    }
}

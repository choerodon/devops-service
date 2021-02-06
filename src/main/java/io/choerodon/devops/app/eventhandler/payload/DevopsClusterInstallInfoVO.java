package io.choerodon.devops.app.eventhandler.payload;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;

import io.choerodon.devops.api.vo.DevopsClusterReqVO;
import io.choerodon.devops.api.vo.HostConnectionVO;
import io.choerodon.devops.infra.dto.DevopsClusterNodeDTO;

public class DevopsClusterInstallInfoVO {
    @ApiModelProperty("操作记录id")
    private Long operationRecordId;
    @ApiModelProperty("项目id")
    private Long projectId;
    @ApiModelProperty("集群id")
    private Long clusterId;
    @ApiModelProperty("需要存储的节点列表")
    private List<DevopsClusterNodeDTO> devopsClusterNodeToSaveDTOList;
    @ApiModelProperty("进行ssh连接的节点")
    private HostConnectionVO hostConnectionVO;
    @ApiModelProperty("集群信息")
    private DevopsClusterReqVO devopsClusterReqVO;
    @ApiModelProperty("保存日志的redisKey")
    private String redisKey;

    public Long getOperationRecordId() {
        return operationRecordId;
    }

    public DevopsClusterInstallInfoVO setOperationRecordId(Long operationRecordId) {
        this.operationRecordId = operationRecordId;
        return this;
    }

    public Long getProjectId() {
        return projectId;
    }

    public DevopsClusterInstallInfoVO setProjectId(Long projectId) {
        this.projectId = projectId;
        return this;
    }

    public Long getClusterId() {
        return clusterId;
    }

    public DevopsClusterInstallInfoVO setClusterId(Long clusterId) {
        this.clusterId = clusterId;
        return this;
    }

    public List<DevopsClusterNodeDTO> getDevopsClusterNodeToSaveDTOList() {
        return devopsClusterNodeToSaveDTOList;
    }

    public DevopsClusterInstallInfoVO setDevopsClusterNodeToSaveDTOList(List<DevopsClusterNodeDTO> devopsClusterNodeToSaveDTOList) {
        this.devopsClusterNodeToSaveDTOList = devopsClusterNodeToSaveDTOList;
        return this;
    }

    public HostConnectionVO getHostConnectionVO() {
        return hostConnectionVO;
    }

    public DevopsClusterInstallInfoVO setHostConnectionVO(HostConnectionVO hostConnectionVO) {
        this.hostConnectionVO = hostConnectionVO;
        return this;
    }

    public DevopsClusterReqVO getDevopsClusterReqVO() {
        return devopsClusterReqVO;
    }

    public DevopsClusterInstallInfoVO setDevopsClusterReqVO(DevopsClusterReqVO devopsClusterReqVO) {
        this.devopsClusterReqVO = devopsClusterReqVO;
        return this;
    }

    public String getRedisKey() {
        return redisKey;
    }

    public DevopsClusterInstallInfoVO setRedisKey(String redisKey) {
        this.redisKey = redisKey;
        return this;
    }
}

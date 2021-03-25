package io.choerodon.devops.api.vo;

import io.swagger.annotations.ApiModelProperty;
import java.util.Map;

public class MiddlewareRedisEnvDeployVO extends MarketInstanceCreationRequestVO {
    @ApiModelProperty("部署模式")
    private String mode;

    @ApiModelProperty("中间件版本")
    private String version;

    /**
     * 部署配置
     */
    private Map<Long, RedisConfigVO> longRedisConfigVOMap;

    public Map<Long, RedisConfigVO> getLongRedisConfigVOMap() {
        return longRedisConfigVOMap;
    }

    public void setLongRedisConfigVOMap(Map<Long, RedisConfigVO> longRedisConfigVOMap) {
        this.longRedisConfigVOMap = longRedisConfigVOMap;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}

package io.choerodon.devops.infra.constant;

/**
 * 和GitLab有关的常量
 *
 * @author zmf
 * @since 10/28/19
 */
public class GitLabConstants {
    private GitLabConstants() {
    }

    /**
     * GitLab组的名称和path格式
     */
    public static final String GITLAB_GROUP_NAME_FORMAT = "%s-%s%s";
    public static final String APP_SERVICE_SUFFIX = "";
    public static final String ENV_GROUP_SUFFIX = "-gitops";
    /**
     * 集群环境库的组 ${orgCode}_${projectCode}-cluster_gitops
     * 这是集群环境库组的后缀
     */
    public static final String CLUSTER_ENV_GROUP_SUFFIX = "-cluster_gitops";
}

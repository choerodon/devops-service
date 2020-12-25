package io.choerodon.devops.infra.feign.operator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.vo.test.ApiTestExecuteVO;
import io.choerodon.devops.api.vo.test.ApiTestTaskRecordVO;
import io.choerodon.devops.infra.dto.test.ApiTestTaskRecordDTO;
import io.choerodon.devops.infra.feign.TestServiceClient;

/**
 * 〈功能简述〉
 * 〈〉
 *
 * @author wanghao
 * @since 2020/9/14 9:28
 */
@Component
public class TestServiceClientOperator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestServiceClientOperator.class);
    @Autowired
    private TestServiceClient testServiceClient;

    /**
     * 执行api测试任务
     *
     * @param projectId
     * @param taskId
     * @param createdBy
     * @return
     */
    public ApiTestTaskRecordDTO executeTask(Long projectId, Long taskId, Long createdBy) {
        ApiTestExecuteVO apiTestExecuteVO = new ApiTestExecuteVO();
        apiTestExecuteVO.setTaskId(taskId);
        ResponseEntity<ApiTestTaskRecordDTO> entity = testServiceClient.executeTask(projectId, apiTestExecuteVO, createdBy);

        if (entity != null && !entity.getStatusCode().is2xxSuccessful()) {
            throw new CommonException("error.execute.api.test.task");
        }
        return entity == null ? null : entity.getBody();
    }

    /**
     * 查询api测试任务记录
     *
     * @param projectId
     * @param taskRecordId
     * @return
     */
    public ApiTestTaskRecordVO queryById(Long projectId, Long taskRecordId) {

        ResponseEntity<ApiTestTaskRecordVO> entity = testServiceClient.queryById(projectId, taskRecordId);

        if (entity != null && !entity.getStatusCode().is2xxSuccessful()) {
            throw new CommonException("error.query.api.test.task.record");
        }
        return entity == null ? null : entity.getBody();
    }

    public boolean testJmeterConnection(String hostIp, Integer jmeterPort) {
        try {
            ResponseEntity<Boolean> result = testServiceClient.testConnection(hostIp, jmeterPort);
            if (result.getStatusCode().is2xxSuccessful()) {
                return Boolean.TRUE.equals(result.getBody());
            }
        } catch (Exception ex) {
            LOGGER.debug("TestServiceClientOperator: Failed to test jmeter connection for host {} and port {}", hostIp, jmeterPort);
            LOGGER.debug("The ex is", ex);
            return false;
        }
        return false;
    }
}

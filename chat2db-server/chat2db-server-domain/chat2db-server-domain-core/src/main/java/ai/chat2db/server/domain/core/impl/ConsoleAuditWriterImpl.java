package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.enums.AuditCategoryEnum;
import ai.chat2db.server.domain.api.model.ConsoleAuditCreateRequest;
import ai.chat2db.server.domain.api.service.ConsoleAuditWriter;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.AuditLogDO;
import ai.chat2db.server.domain.repository.mapper.AuditLogMapper;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.common.config.Chat2dbProperties;
import cn.hutool.core.date.DateUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class ConsoleAuditWriterImpl implements ConsoleAuditWriter {

    @Resource
    private Chat2dbProperties chat2dbProperties;

    private AuditLogMapper getMapper() {
        return Dbutils.getMapper(AuditLogMapper.class);
    }

    @Override
    public DataResult<Long> write(ConsoleAuditCreateRequest request) {
        if (Boolean.FALSE.equals(chat2dbProperties.getAudit().getConsoleEnabled())) {
            return DataResult.of(null);
        }
        AuditLogDO data = new AuditLogDO();
        data.setGmtCreate(DateUtil.date());
        data.setGmtModified(DateUtil.date());
        data.setCategory(AuditCategoryEnum.CONSOLE.getCode());
        data.setActionType(request.getActionType());
        data.setResourceType(request.getResourceType());
        data.setOperatorUserId(request.getOperatorUserId());
        data.setOperatorUserName(request.getOperatorUserName());
        data.setRoleCode(request.getRoleCode());
        data.setTargetId(request.getTargetId());
        data.setTargetName(request.getTargetName());
        data.setRequestPath(request.getRequestPath());
        data.setRequestMethod(request.getRequestMethod());
        data.setRequestId(request.getRequestId());
        data.setClientIp(request.getClientIp());
        data.setUserAgent(request.getUserAgent());
        data.setStatus(request.getStatus());
        data.setDetailSummary(request.getDetailSummary());
        data.setDetailPayload(request.getDetailPayload());
        data.setErrorMessage(request.getErrorMessage());
        getMapper().insert(data);
        return DataResult.of(data.getId());
    }
}

package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.model.AuditRecord;
import ai.chat2db.server.domain.api.model.ConsoleAuditCreateRequest;
import ai.chat2db.server.domain.api.param.audit.AuditPageQueryParam;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;

public interface AuditService {

    DataResult<Long> createConsoleAudit(ConsoleAuditCreateRequest request);

    PageResult<AuditRecord> queryPage(AuditPageQueryParam param);

    DataResult<AuditRecord> queryDetail(String id);
}

package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.model.ConsoleAuditCreateRequest;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;

public interface ConsoleAuditWriter {

    DataResult<Long> write(ConsoleAuditCreateRequest request);
}

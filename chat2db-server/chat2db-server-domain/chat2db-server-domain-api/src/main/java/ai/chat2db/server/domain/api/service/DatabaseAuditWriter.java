package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.model.DatabaseAuditEvent;

public interface DatabaseAuditWriter {

    void write(DatabaseAuditEvent event);
}

package ai.chat2db.server.domain.api.model;

import java.util.Date;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DatabaseAuditEvent {
    private String id;
    private Date timestamp;
    private String requestId;
    private Long userId;
    private String userName;
    private String roleCode;
    private Long dataSourceId;
    private String dataSourceName;
    private String dbType;
    private String databaseName;
    private String schemaName;
    private String sql;
    private String sqlType;
    private String status;
    private Long durationMs;
    private Long operationRows;
    private String clientIp;
    private String clientType;
    private String errorMessage;
}

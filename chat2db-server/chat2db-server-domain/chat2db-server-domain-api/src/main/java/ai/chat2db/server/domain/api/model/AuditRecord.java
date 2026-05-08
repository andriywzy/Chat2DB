package ai.chat2db.server.domain.api.model;

import java.util.Date;
import lombok.Data;

@Data
public class AuditRecord {
    private String id;
    private String category;
    private String actionType;
    private String resourceType;
    private Long operatorUserId;
    private String operatorUserName;
    private String roleCode;
    private String targetId;
    private String targetName;
    private String status;
    private Date occurredAt;
    private String detailSummary;
    private String detailPayload;
    private String requestId;
    private String requestPath;
    private String requestMethod;
    private String clientIp;
    private String userAgent;
    private Long dataSourceId;
    private String dataSourceName;
    private String sqlType;
    private Long durationMs;
    private Long operationRows;
    private String errorMessage;
}

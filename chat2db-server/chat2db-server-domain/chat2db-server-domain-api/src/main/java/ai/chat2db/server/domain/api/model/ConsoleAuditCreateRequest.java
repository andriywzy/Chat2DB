package ai.chat2db.server.domain.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConsoleAuditCreateRequest {
    private String actionType;
    private String resourceType;
    private Long operatorUserId;
    private String operatorUserName;
    private String roleCode;
    private String targetId;
    private String targetName;
    private String requestPath;
    private String requestMethod;
    private String requestId;
    private String clientIp;
    private String userAgent;
    private String status;
    private String detailSummary;
    private String detailPayload;
    private String errorMessage;
}

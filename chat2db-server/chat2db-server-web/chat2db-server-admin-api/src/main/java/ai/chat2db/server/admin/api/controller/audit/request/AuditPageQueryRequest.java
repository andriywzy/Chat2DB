package ai.chat2db.server.admin.api.controller.audit.request;

import ai.chat2db.server.common.api.controller.request.CommonPageQueryRequest;
import lombok.Data;

@Data
public class AuditPageQueryRequest extends CommonPageQueryRequest {
    private String category;
    private String resourceType;
    private String status;
    private Long operatorUserId;
    private String targetId;
    private Long dataSourceId;
    private Long startTime;
    private Long endTime;
}

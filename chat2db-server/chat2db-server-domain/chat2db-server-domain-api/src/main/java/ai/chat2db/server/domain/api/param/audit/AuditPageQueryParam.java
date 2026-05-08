package ai.chat2db.server.domain.api.param.audit;

import ai.chat2db.server.tools.base.wrapper.param.PageQueryParam;
import lombok.Data;

@Data
public class AuditPageQueryParam extends PageQueryParam {
    private String category;
    private String resourceType;
    private String status;
    private String searchKey;
    private Long operatorUserId;
    private String targetId;
    private Long dataSourceId;
    private Long startTime;
    private Long endTime;
}

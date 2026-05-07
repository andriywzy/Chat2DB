package ai.chat2db.server.web.api.controller.rdb.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GlobalObjectSearchItemVO {

    private Long dataSourceId;

    private String dataSourceName;

    private String databaseType;

    private Boolean supportDatabase;

    private Boolean supportSchema;

    private String databaseName;

    private String schemaName;

    private String objectType;

    private String objectName;

    private String comment;
}

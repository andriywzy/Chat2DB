package ai.chat2db.server.domain.api.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ObjectSearchIndexItem {

    private Long dataSourceId;

    private String dataSourceName;

    private String databaseType;

    private String databaseName;

    private String schemaName;

    private String objectType;

    private String objectName;

    private String comment;
}

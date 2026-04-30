package ai.chat2db.server.web.api.controller.ai.platform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSchemaSource {

    private Long dataSourceId;

    private String dataSourceAlias;

    private String databaseName;

    private String schemaName;

    private String tableName;

    private Boolean currentDataSource;

    private String sourceType;

    private Double score;
}

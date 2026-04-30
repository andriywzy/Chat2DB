package ai.chat2db.server.web.api.controller.ai.platform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRetrievalQuery {

    private Long userId;

    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    private String message;

    private Integer limit;

    private List<Long> documentIds;

    private Boolean refresh;
}

package ai.chat2db.server.web.api.controller.data.source.request;

import java.util.List;

import lombok.Data;

@Data
public class DataSourceImportRequest {

    private String version;

    private String template;

    private Long defaultEnvironmentId;

    private List<DataSourceImportItemRequest> connections;
}

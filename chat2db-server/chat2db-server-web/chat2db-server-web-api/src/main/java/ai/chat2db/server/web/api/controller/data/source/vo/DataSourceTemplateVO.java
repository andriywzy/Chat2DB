package ai.chat2db.server.web.api.controller.data.source.vo;

import java.util.List;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceImportItemRequest;
import lombok.Data;

@Data
public class DataSourceTemplateVO {

    private String version;

    private String template;

    private String exportedAt;

    private List<DataSourceImportItemRequest> connections;
}

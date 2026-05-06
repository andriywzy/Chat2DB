package ai.chat2db.server.web.api.controller.data.source.request;

import java.util.List;

import lombok.Data;

@Data
public class DataSourceExportRequest {

    private List<Long> ids;
}

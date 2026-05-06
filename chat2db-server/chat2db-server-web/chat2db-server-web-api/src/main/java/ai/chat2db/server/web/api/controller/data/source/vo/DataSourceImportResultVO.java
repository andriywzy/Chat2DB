package ai.chat2db.server.web.api.controller.data.source.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class DataSourceImportResultVO {

    private int total;

    private int successCount;

    private int failureCount;

    private List<Long> createdIds = new ArrayList<>();

    private List<String> errors = new ArrayList<>();
}

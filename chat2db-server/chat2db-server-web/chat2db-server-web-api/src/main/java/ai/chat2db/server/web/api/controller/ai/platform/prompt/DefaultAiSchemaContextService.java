package ai.chat2db.server.web.api.controller.ai.platform.prompt;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.ShowCreateTableParam;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import com.alibaba.fastjson2.JSON;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultAiSchemaContextService implements AiSchemaContextService {

    private final TableService tableService;
    private final DataSourceService dataSourceService;

    public DefaultAiSchemaContextService(TableService tableService, DataSourceService dataSourceService) {
        this.tableService = tableService;
        this.dataSourceService = dataSourceService;
    }

    @Override
    public String queryDatabaseType(ChatQueryRequest queryRequest) {
        if (queryRequest == null || queryRequest.getDataSourceId() == null) {
            return "MYSQL";
        }
        DataResult<DataSource> dataResult = dataSourceService.queryById(queryRequest.getDataSourceId());
        if (dataResult == null || dataResult.getData() == null) {
            return "MYSQL";
        }
        String dataSourceType = dataResult.getData().getType();
        if (StringUtils.isBlank(dataSourceType)) {
            dataSourceType = "MYSQL";
        }
        return dataSourceType;
    }

    @Override
    public List<String> buildSelectedTableSchemas(ChatQueryRequest queryRequest) {
        if (queryRequest == null || queryRequest.getDataSourceId() == null
            || CollectionUtils.isEmpty(queryRequest.getTableNames())) {
            return List.of();
        }
        List<String> schemaContent = new ArrayList<>();
        for (String tableName : queryRequest.getTableNames()) {
            String ddl = queryTableDdl(tableName, queryRequest);
            if (StringUtils.isNotBlank(ddl)) {
                schemaContent.add(ddl);
            }
        }
        return schemaContent;
    }

    private String queryTableDdl(String tableName, ChatQueryRequest request) {
        ShowCreateTableParam param = new ShowCreateTableParam();
        param.setTableName(tableName);
        param.setDataSourceId(request.getDataSourceId());
        param.setDatabaseName(request.getDatabaseName());
        param.setSchemaName(request.getSchemaName());
        return tableService.showCreateTable(param).getData();
    }
}

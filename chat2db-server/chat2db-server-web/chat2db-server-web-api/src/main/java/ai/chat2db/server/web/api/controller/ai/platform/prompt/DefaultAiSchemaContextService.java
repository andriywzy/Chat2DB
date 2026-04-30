package ai.chat2db.server.web.api.controller.ai.platform.prompt;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.ShowCreateTableParam;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoHandler;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalContext;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiSchemaSource;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.spi.sql.Chat2DBContext;
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
    private final ConnectionInfoHandler connectionInfoHandler;

    public DefaultAiSchemaContextService(
        TableService tableService,
        DataSourceService dataSourceService,
        ConnectionInfoHandler connectionInfoHandler
    ) {
        this.tableService = tableService;
        this.dataSourceService = dataSourceService;
        this.connectionInfoHandler = connectionInfoHandler;
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
        return buildSelectedSchemaContext(queryRequest).getSchemaSnippets();
    }

    @Override
    public AiRetrievalContext buildSelectedSchemaContext(ChatQueryRequest queryRequest) {
        if (queryRequest == null || queryRequest.getDataSourceId() == null
            || CollectionUtils.isEmpty(queryRequest.getTableNames())) {
            return AiRetrievalContext.builder()
                .schemaSnippets(List.of())
                .schemaSources(List.of())
                .build();
        }
        List<String> schemaContent = new ArrayList<>();
        List<AiSchemaSource> schemaSources = new ArrayList<>();
        DataResult<DataSource> dataSourceResult = dataSourceService.queryById(queryRequest.getDataSourceId());
        DataSource dataSource = dataSourceResult == null ? null : dataSourceResult.getData();
        for (String tableName : queryRequest.getTableNames()) {
            String ddl = queryTableDdl(tableName, queryRequest);
            if (StringUtils.isNotBlank(ddl)) {
                schemaContent.add(ddl);
                schemaSources.add(AiSchemaSource.builder()
                    .dataSourceId(queryRequest.getDataSourceId())
                    .dataSourceAlias(dataSource == null ? null : dataSource.getAlias())
                    .databaseName(queryRequest.getDatabaseName())
                    .schemaName(queryRequest.getSchemaName())
                    .tableName(tableName)
                    .currentDataSource(Boolean.TRUE)
                    .sourceType("SELECTED")
                    .score(100D)
                    .build());
            }
        }
        return AiRetrievalContext.builder()
            .schemaSnippets(schemaContent)
            .schemaSources(schemaSources)
            .build();
    }

    private String queryTableDdl(String tableName, ChatQueryRequest request) {
        try {
            Chat2DBContext.putContext(
                connectionInfoHandler.toInfo(request.getDataSourceId(), request.getDatabaseName(), null, request.getSchemaName())
            );
            ShowCreateTableParam param = new ShowCreateTableParam();
            param.setTableName(tableName);
            param.setDataSourceId(request.getDataSourceId());
            param.setDatabaseName(request.getDatabaseName());
            param.setSchemaName(request.getSchemaName());
            return tableService.showCreateTable(param).getData();
        } finally {
            Chat2DBContext.removeContext();
        }
    }
}

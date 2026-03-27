package ai.chat2db.server.web.api.controller.ai.service;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.ShowCreateTableParam;
import ai.chat2db.server.domain.api.param.TableQueryParam;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.tools.base.enums.WhiteListTypeEnum;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.common.util.EasyEnumUtils;
import ai.chat2db.server.web.api.controller.ai.chat2db.client.Chat2dbAIClient;
import ai.chat2db.server.web.api.controller.ai.converter.ChatConverter;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.fastchat.client.FastChatAIClient;
import ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.http.GatewayClientService;
import ai.chat2db.server.web.api.http.model.EsTableSchema;
import ai.chat2db.server.web.api.http.model.TableSchema;
import ai.chat2db.server.web.api.http.request.EsTableSchemaRequest;
import ai.chat2db.server.web.api.http.request.TableSchemaRequest;
import ai.chat2db.server.web.api.http.request.WhiteListRequest;
import ai.chat2db.server.web.api.http.response.EsTableSchemaResponse;
import ai.chat2db.server.web.api.http.response.TableSchemaResponse;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AiPromptService {

    @Autowired
    private TableService tableService;

    @Autowired
    private ChatConverter chatConverter;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private GatewayClientService gatewayClientService;

    @Autowired
    private AiConfigurationService aiConfigurationService;

    public String buildPrompt(ChatQueryRequest queryRequest) {
        if (PromptType.TEXT_GENERATION.getCode().equals(queryRequest.getPromptType())) {
            return queryRequest.getMessage();
        }

        String dataSourceType = queryDatabaseType(queryRequest);
        String properties = "";
        if (CollectionUtils.isNotEmpty(queryRequest.getTableNames())) {
            TableQueryParam queryParam = chatConverter.chat2tableQuery(queryRequest);
            properties = buildTableColumn(queryParam, queryRequest.getTableNames());
        } else {
            properties = mappingDatabaseSchema(queryRequest);
        }
        String prompt = queryRequest.getMessage();
        String promptType = StringUtils.isBlank(queryRequest.getPromptType()) ? PromptType.NL_2_SQL.getCode()
            : queryRequest.getPromptType();
        PromptType pType = EasyEnumUtils.getEnum(PromptType.class, promptType);
        String ext = StringUtils.isNotBlank(queryRequest.getExt()) ? queryRequest.getExt() : "";
        String schemaProperty = StringUtils.isNotEmpty(properties) ? String.format(
            "### Please follow the below table properties and SQL input%s. %s\n#\n### %s SQL tables, with their properties:\n#\n# "
                + "%s\n#\n#\n### SQL input: %s", pType.getDescription(), ext, dataSourceType,
            properties, prompt) : String.format("### Please follow the below SQL input%s. %s\n#\n### SQL input: %s",
            pType.getDescription(), ext, prompt);
        switch (pType) {
            case SQL_2_SQL:
                schemaProperty = StringUtils.isNotBlank(queryRequest.getDestSqlType()) ? String.format(
                    "%s\n#\n### Target SQL type: %s", schemaProperty, queryRequest.getDestSqlType()) : String.format(
                    "%s\n#\n### Target SQL type: %s", schemaProperty, dataSourceType);
            default:
                break;
        }
        return schemaProperty.replaceAll("[\r\t]", "");
    }

    public String getApiKey() {
        return aiConfigurationService.getChat2dbApiKey();
    }

    public String queryDatabaseType(ChatQueryRequest queryRequest) {
        DataResult<DataSource> dataResult = dataSourceService.queryById(queryRequest.getDataSourceId());
        String dataSourceType = dataResult.getData().getType();
        if (StringUtils.isBlank(dataSourceType)) {
            dataSourceType = "MYSQL";
        }
        return dataSourceType;
    }

    public String mappingDatabaseSchema(ChatQueryRequest queryRequest) {
        String properties = "";
        String apiKey = getApiKey();
        if (StringUtils.isNotBlank(apiKey)) {
            boolean res = gatewayClientService.checkInWhite(new WhiteListRequest(apiKey, WhiteListTypeEnum.VECTOR.getCode())).getData();
            if (res) {
                properties = queryDatabaseSchema(queryRequest);
            }
        }
        return properties;
    }

    public String queryDatabaseSchema(ChatQueryRequest queryRequest) {
        FastChatEmbeddingResponse response = distributeAIEmbedding(queryRequest.getMessage());
        List<List<BigDecimal>> contentVector = new ArrayList<>();
        if (Objects.isNull(response) || CollectionUtils.isEmpty(response.getData())) {
            return "";
        }
        contentVector.add(response.getData().get(0).getEmbedding());

        TableSchemaRequest tableSchemaRequest = new TableSchemaRequest();
        tableSchemaRequest.setSchemaVector(contentVector);
        tableSchemaRequest.setDataSourceId(queryRequest.getDataSourceId());
        tableSchemaRequest.setDatabaseName(queryRequest.getDatabaseName());
        tableSchemaRequest.setDataSourceSchema(queryRequest.getSchemaName());
        String apiKey = getApiKey();
        if (StringUtils.isBlank(apiKey)) {
            return "";
        }
        tableSchemaRequest.setApiKey(apiKey);
        try {
            DataResult<TableSchemaResponse> result = gatewayClientService.schemaVectorSearch(tableSchemaRequest);
            List<String> schemas = Lists.newArrayList();
            if (Objects.nonNull(result.getData()) && CollectionUtils.isNotEmpty(result.getData().getTableSchemas())) {
                for (TableSchema data : result.getData().getTableSchemas()) {
                    schemas.add(data.getTableSchema());
                }
            }
            if (CollectionUtils.isEmpty(schemas)) {
                return "";
            }
            String res = JSON.toJSONString(schemas);
            log.info("search vector result:{}", res);
            return res;
        } catch (Exception exception) {
            log.error("query table error, do nothing");
            return "";
        }
    }

    public String querySchemaByEs(ChatQueryRequest queryRequest) {
        EsTableSchemaRequest tableSchemaRequest = new EsTableSchemaRequest();
        tableSchemaRequest.setSearchKey(queryRequest.getMessage());
        tableSchemaRequest.setDataSourceId(queryRequest.getDataSourceId());
        tableSchemaRequest.setDatabaseName(queryRequest.getDatabaseName());
        tableSchemaRequest.setSchemaName(queryRequest.getSchemaName());
        String apiKey = getApiKey();
        if (StringUtils.isBlank(apiKey)) {
            return "";
        }
        tableSchemaRequest.setApiKey(apiKey);
        try {
            DataResult<EsTableSchemaResponse> result = gatewayClientService.schemaEsSearch(tableSchemaRequest);
            List<String> schemas = Lists.newArrayList();
            if (Objects.nonNull(result.getData()) && CollectionUtils.isNotEmpty(result.getData().getTableSchemas())) {
                for (EsTableSchema data : result.getData().getTableSchemas()) {
                    schemas.add(data.getTableSchemaContent());
                }
            }
            if (CollectionUtils.isEmpty(schemas)) {
                return "";
            }
            String res = JSON.toJSONString(schemas);
            log.info("search es result:{}", res);
            return res;
        } catch (Exception exception) {
            log.error("query es table error, do nothing");
            return "";
        }
    }

    public FastChatEmbeddingResponse distributeAIEmbedding(String input) {
        AiSqlSourceEnum aiSqlSourceEnum = aiConfigurationService.getCurrentAiSqlSource();
        switch (aiSqlSourceEnum) {
            case CHAT2DBAI:
                return Chat2dbAIClient.getInstance().embeddings(input);
            case FASTCHATAI:
                return FastChatAIClient.getInstance().embeddings(input);
            default:
                return null;
        }
    }

    private String buildTableColumn(TableQueryParam tableQueryParam, List<String> tableNames) {
        if (CollectionUtils.isEmpty(tableNames)) {
            return "";
        }
        List<String> schemaContent = Lists.newArrayList();
        try {
            schemaContent = tableNames.stream().map(tableName -> {
                tableQueryParam.setTableName(tableName);
                return queryTableDdl(tableName, tableQueryParam);
            }).collect(Collectors.toList());
        } catch (Exception exception) {
            log.error("query table error, do nothing");
        }

        return JSON.toJSONString(schemaContent);
    }

    private String queryTableDdl(String tableName, TableQueryParam request) {
        ShowCreateTableParam param = new ShowCreateTableParam();
        param.setTableName(tableName);
        param.setDataSourceId(request.getDataSourceId());
        param.setDatabaseName(request.getDatabaseName());
        param.setSchemaName(request.getSchemaName());
        DataResult<String> tableSchema = tableService.showCreateTable(param);
        return tableSchema.getData();
    }
}

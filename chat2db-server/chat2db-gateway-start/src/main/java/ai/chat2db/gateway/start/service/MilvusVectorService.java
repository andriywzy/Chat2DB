package ai.chat2db.gateway.start.service;

import ai.chat2db.gateway.start.config.MilvusProperties;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.http.model.Knowledge;
import ai.chat2db.server.web.api.http.model.TableSchema;
import ai.chat2db.server.web.api.http.request.KnowledgeRequest;
import ai.chat2db.server.web.api.http.request.TableSchemaRequest;
import ai.chat2db.server.web.api.http.response.KnowledgeResponse;
import ai.chat2db.server.web.api.http.response.TableSchemaResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class MilvusVectorService {

    private static final String EMBEDDING_FIELD = "embedding";
    private static final String ID_FIELD = "id";
    private static final String CONTENT_FIELD = "content";
    private static final String WORD_COUNT_FIELD = "wordCount";
    private static final String DOCUMENT_ID_FIELD = "documentId";
    private static final String USER_ID_FIELD = "userId";
    private static final String DATA_SOURCE_ID_FIELD = "dataSourceId";
    private static final String DATABASE_NAME_FIELD = "databaseName";
    private static final String DATA_SOURCE_SCHEMA_FIELD = "dataSourceSchema";
    private static final String TABLE_SCHEMA_FIELD = "tableSchema";

    private final AtomicLong idGenerator = new AtomicLong(System.currentTimeMillis());

    @Resource
    private MilvusClientV2 milvusClient;

    @Resource
    private MilvusProperties properties;

    public ActionResult saveKnowledge(KnowledgeRequest request) {
        try {
            List<JsonObject> rows = buildKnowledgeRows(request);
            if (rows.isEmpty()) {
                return ActionResult.fail("milvus.knowledge.empty", "No knowledge vector rows to save", null);
            }
            ensureKnowledgeCollection(vectorDimension(request.getContentVector()));
            ensureCollectionLoaded(properties.getKnowledgeCollection());
            milvusClient.insert(InsertReq.builder()
                .collectionName(properties.getKnowledgeCollection())
                .data(rows)
                .build());
            return ActionResult.isSuccess();
        } catch (Exception ex) {
            log.error("save knowledge vector error", ex);
            return ActionResult.fail("milvus.knowledge.save.failed", "Failed to save knowledge vectors", ex.getMessage());
        }
    }

    public DataResult<KnowledgeResponse> searchKnowledge(KnowledgeRequest request) {
        try {
            if (CollectionUtils.isEmpty(request.getContentVector()) || CollectionUtils.isEmpty(request.getContentVector().get(0))) {
                return DataResult.of(new KnowledgeResponse(Collections.emptyList()));
            }
            if (!hasCollection(properties.getKnowledgeCollection())) {
                return DataResult.of(new KnowledgeResponse(Collections.emptyList()));
            }
            ensureCollectionLoaded(properties.getKnowledgeCollection());
            SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(properties.getKnowledgeCollection())
                .annsField(EMBEDDING_FIELD)
                .metricType(metricType())
                .data(Collections.singletonList(new FloatVec(toFloatVector(request.getContentVector().get(0)))))
                .topK(properties.getTopK())
                .outputFields(List.of(ID_FIELD, DOCUMENT_ID_FIELD, CONTENT_FIELD, WORD_COUNT_FIELD));
            String filter = buildKnowledgeFilter(request);
            if (StringUtils.isNotBlank(filter)) {
                builder.filter(filter);
            }
            SearchResp response = milvusClient.search(builder.build());
            return DataResult.of(new KnowledgeResponse(parseKnowledgeResponse(response)));
        } catch (Exception ex) {
            log.error("search knowledge vector error", ex);
            return DataResult.error("milvus.knowledge.search.failed", "Failed to search knowledge vectors");
        }
    }

    public ActionResult deleteKnowledge(KnowledgeRequest request) {
        try {
            if (!hasCollection(properties.getKnowledgeCollection())) {
                return ActionResult.isSuccess();
            }
            String filter = buildKnowledgeDeleteFilter(request);
            if (StringUtils.isBlank(filter)) {
                return ActionResult.fail("milvus.knowledge.delete.filter.empty", "Knowledge delete filter is required", null);
            }
            milvusClient.delete(DeleteReq.builder()
                .collectionName(properties.getKnowledgeCollection())
                .filter(filter)
                .build());
            return ActionResult.isSuccess();
        } catch (Exception ex) {
            log.error("delete knowledge vector error", ex);
            return ActionResult.fail("milvus.knowledge.delete.failed", "Failed to delete knowledge vectors", ex.getMessage());
        }
    }

    public ActionResult saveSchema(TableSchemaRequest request) {
        try {
            if (request.getDeleteBeforeInsert() != null && request.getDeleteBeforeInsert()) {
                deleteSchema(request);
            }
            List<JsonObject> rows = buildSchemaRows(request);
            if (rows.isEmpty()) {
                return ActionResult.fail("milvus.schema.empty", "No schema vector rows to save", null);
            }
            ensureSchemaCollection(vectorDimension(request.getSchemaVector()));
            ensureCollectionLoaded(properties.getSchemaCollection());
            milvusClient.insert(InsertReq.builder()
                .collectionName(properties.getSchemaCollection())
                .data(rows)
                .build());
            return ActionResult.isSuccess();
        } catch (Exception ex) {
            log.error("save schema vector error", ex);
            return ActionResult.fail("milvus.schema.save.failed", "Failed to save schema vectors", ex.getMessage());
        }
    }

    public DataResult<TableSchemaResponse> searchSchema(TableSchemaRequest request) {
        try {
            if (CollectionUtils.isEmpty(request.getSchemaVector()) || CollectionUtils.isEmpty(request.getSchemaVector().get(0))) {
                return DataResult.of(new TableSchemaResponse(Collections.emptyList()));
            }
            if (!hasCollection(properties.getSchemaCollection())) {
                return DataResult.of(new TableSchemaResponse(Collections.emptyList()));
            }
            ensureCollectionLoaded(properties.getSchemaCollection());
            SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(properties.getSchemaCollection())
                .annsField(EMBEDDING_FIELD)
                .metricType(metricType())
                .data(Collections.singletonList(new FloatVec(toFloatVector(request.getSchemaVector().get(0)))))
                .topK(properties.getTopK())
                .outputFields(List.of(ID_FIELD, DATA_SOURCE_ID_FIELD, TABLE_SCHEMA_FIELD, WORD_COUNT_FIELD));
            String filter = buildSchemaFilter(request);
            if (StringUtils.isNotBlank(filter)) {
                builder.filter(filter);
            }
            SearchResp response = milvusClient.search(builder.build());
            return DataResult.of(new TableSchemaResponse(parseSchemaResponse(response)));
        } catch (Exception ex) {
            log.error("search schema vector error", ex);
            return DataResult.error("milvus.schema.search.failed", "Failed to search schema vectors");
        }
    }

    private List<JsonObject> buildKnowledgeRows(KnowledgeRequest request) {
        List<JsonObject> rows = new ArrayList<>();
        if (CollectionUtils.isEmpty(request.getSentenceList()) || CollectionUtils.isEmpty(request.getContentVector())) {
            return rows;
        }
        int rowCount = Math.min(request.getSentenceList().size(), request.getContentVector().size());
        for (int i = 0; i < rowCount; i++) {
            List<BigDecimal> vector = request.getContentVector().get(i);
            String sentence = request.getSentenceList().get(i);
            if (CollectionUtils.isEmpty(vector) || StringUtils.isBlank(sentence)) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty(ID_FIELD, nextId());
            row.addProperty(DOCUMENT_ID_FIELD, request.getDocumentId());
            row.addProperty(USER_ID_FIELD, request.getUserId());
            row.addProperty(CONTENT_FIELD, sentence);
            row.addProperty(WORD_COUNT_FIELD, sentence.length());
            row.add(EMBEDDING_FIELD, toJsonArray(vector));
            rows.add(row);
        }
        return rows;
    }

    private List<JsonObject> buildSchemaRows(TableSchemaRequest request) {
        List<JsonObject> rows = new ArrayList<>();
        if (CollectionUtils.isEmpty(request.getSchemaList()) || CollectionUtils.isEmpty(request.getSchemaVector())) {
            return rows;
        }
        int rowCount = Math.min(request.getSchemaList().size(), request.getSchemaVector().size());
        for (int i = 0; i < rowCount; i++) {
            List<BigDecimal> vector = request.getSchemaVector().get(i);
            String schema = request.getSchemaList().get(i);
            if (CollectionUtils.isEmpty(vector) || StringUtils.isBlank(schema)) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty(ID_FIELD, nextId());
            row.addProperty(DATA_SOURCE_ID_FIELD, request.getDataSourceId());
            row.addProperty(DATABASE_NAME_FIELD, StringUtils.defaultString(request.getDatabaseName()));
            row.addProperty(DATA_SOURCE_SCHEMA_FIELD, StringUtils.defaultString(request.getDataSourceSchema()));
            row.addProperty(TABLE_SCHEMA_FIELD, schema);
            row.addProperty(WORD_COUNT_FIELD, schema.length());
            row.add(EMBEDDING_FIELD, toJsonArray(vector));
            rows.add(row);
        }
        return rows;
    }

    private void deleteSchema(TableSchemaRequest request) {
        if (!hasCollection(properties.getSchemaCollection())) {
            return;
        }
        String filter = buildSchemaDeleteFilter(request);
        if (StringUtils.isBlank(filter)) {
            return;
        }
        milvusClient.delete(DeleteReq.builder()
            .collectionName(properties.getSchemaCollection())
            .filter(filter)
            .build());
    }

    private synchronized void ensureKnowledgeCollection(int dimension) {
        if (hasCollection(properties.getKnowledgeCollection())) {
            return;
        }
        milvusClient.createCollection(CreateCollectionReq.builder()
            .collectionName(properties.getKnowledgeCollection())
            .collectionSchema(createKnowledgeSchema(dimension))
            .indexParams(Collections.singletonList(createVectorIndex()))
            .build());
        ensureCollectionLoaded(properties.getKnowledgeCollection());
    }

    private synchronized void ensureSchemaCollection(int dimension) {
        if (hasCollection(properties.getSchemaCollection())) {
            return;
        }
        milvusClient.createCollection(CreateCollectionReq.builder()
            .collectionName(properties.getSchemaCollection())
            .collectionSchema(createSchemaSchema(dimension))
            .indexParams(Collections.singletonList(createVectorIndex()))
            .build());
        ensureCollectionLoaded(properties.getSchemaCollection());
    }

    private CreateCollectionReq.CollectionSchema createKnowledgeSchema(int dimension) {
        CreateCollectionReq.CollectionSchema schema = milvusClient.createSchema();
        schema.addField(AddFieldReq.builder().fieldName(ID_FIELD).dataType(DataType.Int64).isPrimaryKey(Boolean.TRUE).autoID(Boolean.FALSE).build());
        schema.addField(AddFieldReq.builder().fieldName(DOCUMENT_ID_FIELD).dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName(USER_ID_FIELD).dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName(CONTENT_FIELD).dataType(DataType.VarChar).maxLength(properties.getVarcharMaxLength()).build());
        schema.addField(AddFieldReq.builder().fieldName(WORD_COUNT_FIELD).dataType(DataType.Int32).build());
        schema.addField(AddFieldReq.builder().fieldName(EMBEDDING_FIELD).dataType(DataType.FloatVector).dimension(dimension).build());
        return schema;
    }

    private CreateCollectionReq.CollectionSchema createSchemaSchema(int dimension) {
        CreateCollectionReq.CollectionSchema schema = milvusClient.createSchema();
        schema.addField(AddFieldReq.builder().fieldName(ID_FIELD).dataType(DataType.Int64).isPrimaryKey(Boolean.TRUE).autoID(Boolean.FALSE).build());
        schema.addField(AddFieldReq.builder().fieldName(DATA_SOURCE_ID_FIELD).dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName(DATABASE_NAME_FIELD).dataType(DataType.VarChar).maxLength(255).build());
        schema.addField(AddFieldReq.builder().fieldName(DATA_SOURCE_SCHEMA_FIELD).dataType(DataType.VarChar).maxLength(255).build());
        schema.addField(AddFieldReq.builder().fieldName(TABLE_SCHEMA_FIELD).dataType(DataType.VarChar).maxLength(properties.getVarcharMaxLength()).build());
        schema.addField(AddFieldReq.builder().fieldName(WORD_COUNT_FIELD).dataType(DataType.Int32).build());
        schema.addField(AddFieldReq.builder().fieldName(EMBEDDING_FIELD).dataType(DataType.FloatVector).dimension(dimension).build());
        return schema;
    }

    private IndexParam createVectorIndex() {
        return IndexParam.builder()
            .fieldName(EMBEDDING_FIELD)
            .indexType(IndexParam.IndexType.AUTOINDEX)
            .metricType(metricType())
            .build();
    }

    private boolean hasCollection(String collectionName) {
        return milvusClient.hasCollection(HasCollectionReq.builder().collectionName(collectionName).build());
    }

    private void ensureCollectionLoaded(String collectionName) {
        milvusClient.loadCollection(LoadCollectionReq.builder().collectionName(collectionName).build());
    }

    private List<Knowledge> parseKnowledgeResponse(SearchResp response) {
        List<Knowledge> knowledgeList = new ArrayList<>();
        if (response == null || CollectionUtils.isEmpty(response.getSearchResults())) {
            return knowledgeList;
        }
        for (SearchResp.SearchResult item : response.getSearchResults().get(0)) {
            Map<String, Object> entity = item.getEntity();
            if (entity == null) {
                continue;
            }
            knowledgeList.add(new Knowledge(
                toLong(entity.get(ID_FIELD)),
                toLong(entity.get(DOCUMENT_ID_FIELD)),
                Objects.toString(entity.get(CONTENT_FIELD), null),
                toInteger(entity.get(WORD_COUNT_FIELD)),
                item.getScore()
            ));
        }
        return knowledgeList;
    }

    private List<TableSchema> parseSchemaResponse(SearchResp response) {
        List<TableSchema> tableSchemas = new ArrayList<>();
        if (response == null || CollectionUtils.isEmpty(response.getSearchResults())) {
            return tableSchemas;
        }
        for (SearchResp.SearchResult item : response.getSearchResults().get(0)) {
            Map<String, Object> entity = item.getEntity();
            if (entity == null) {
                continue;
            }
            tableSchemas.add(new TableSchema(
                toLong(entity.get(ID_FIELD)),
                toLong(entity.get(DATA_SOURCE_ID_FIELD)),
                Objects.toString(entity.get(TABLE_SCHEMA_FIELD), null),
                toInteger(entity.get(WORD_COUNT_FIELD))
            ));
        }
        return tableSchemas;
    }

    private String buildSchemaFilter(TableSchemaRequest request) {
        List<String> filters = new ArrayList<>();
        if (request.getDataSourceId() != null) {
            filters.add(DATA_SOURCE_ID_FIELD + " == " + request.getDataSourceId());
        }
        if (StringUtils.isNotBlank(request.getDatabaseName())) {
            filters.add(DATABASE_NAME_FIELD + " == \"" + escape(request.getDatabaseName()) + "\"");
        }
        if (StringUtils.isNotBlank(request.getDataSourceSchema())) {
            filters.add(DATA_SOURCE_SCHEMA_FIELD + " == \"" + escape(request.getDataSourceSchema()) + "\"");
        }
        return String.join(" and ", filters);
    }

    private String buildKnowledgeFilter(KnowledgeRequest request) {
        List<String> filters = new ArrayList<>();
        if (request.getUserId() != null) {
            filters.add(USER_ID_FIELD + " == " + request.getUserId());
        }
        if (request.getDocumentId() != null) {
            filters.add(DOCUMENT_ID_FIELD + " == " + request.getDocumentId());
        }
        if (CollectionUtils.isNotEmpty(request.getDocumentIds())) {
            String joinedIds = request.getDocumentIds().stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse(StringUtils.EMPTY);
            if (StringUtils.isNotBlank(joinedIds)) {
                filters.add(DOCUMENT_ID_FIELD + " in [" + joinedIds + "]");
            }
        }
        return String.join(" and ", filters);
    }

    private String buildKnowledgeDeleteFilter(KnowledgeRequest request) {
        String filter = buildKnowledgeFilter(request);
        return StringUtils.defaultIfBlank(filter, null);
    }

    private String buildSchemaDeleteFilter(TableSchemaRequest request) {
        if (request.getDataSourceId() == null) {
            return null;
        }
        return buildSchemaFilter(request);
    }

    private JsonArray toJsonArray(List<BigDecimal> vector) {
        JsonArray array = new JsonArray();
        for (BigDecimal value : vector) {
            array.add(value.floatValue());
        }
        return array;
    }

    private List<Float> toFloatVector(List<BigDecimal> vector) {
        List<Float> floatVector = new ArrayList<>(vector.size());
        for (BigDecimal value : vector) {
            floatVector.add(value.floatValue());
        }
        return floatVector;
    }

    private int vectorDimension(List<List<BigDecimal>> vectors) {
        if (CollectionUtils.isEmpty(vectors) || CollectionUtils.isEmpty(vectors.get(0))) {
            throw new IllegalArgumentException("Vector dimension cannot be empty");
        }
        return vectors.get(0).size();
    }

    private long nextId() {
        return idGenerator.incrementAndGet();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private IndexParam.MetricType metricType() {
        return IndexParam.MetricType.valueOf(StringUtils.upperCase(properties.getMetricType()));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }
}

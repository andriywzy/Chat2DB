package ai.chat2db.server.web.api.controller.redis;

import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.redis.request.RedisKeyCreateRequest;
import ai.chat2db.server.web.api.controller.redis.request.RedisKeyDeleteRequest;
import ai.chat2db.server.web.api.controller.redis.request.RedisKeyDetailRequest;
import ai.chat2db.server.web.api.controller.redis.request.RedisKeyPageRequest;
import ai.chat2db.server.web.api.controller.redis.request.RedisKeySaveRequest;
import ai.chat2db.server.web.api.controller.redis.vo.RedisKeyDetailVO;
import ai.chat2db.server.web.api.controller.redis.vo.RedisKeyVO;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.SQLExecutor;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@ConnectionInfoAspect
@RequestMapping("/api/redis/browser")
@RestController
public class RedisBrowserController {

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int PREVIEW_LIST_LIMIT = 20;
    private static final int PREVIEW_TEXT_LIMIT = 240;

    private static final String TYPE_STRING = "string";
    private static final String TYPE_HASH = "hash";
    private static final String TYPE_LIST = "list";
    private static final String TYPE_SET = "set";
    private static final String TYPE_ZSET = "zset";
    private static final String TYPE_STREAM = "stream";

    @GetMapping("/key_page")
    public WebPageResult<RedisKeyVO> keyPage(@Valid RedisKeyPageRequest request) {
        int pageNo = request.getPageNo() == null || request.getPageNo() < 1 ? DEFAULT_PAGE_NO : request.getPageNo();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1 ? DEFAULT_PAGE_SIZE : request.getPageSize();
        Connection connection = Chat2DBContext.getConnection();
        if (connection == null) {
            return WebPageResult.empty(pageNo, pageSize);
        }

        try {
            List<String> allKeys = queryRedisKeys(connection, normalizeRedisPattern(request.getSearchKey()));
            if (CollectionUtils.isEmpty(allKeys)) {
                return WebPageResult.empty(pageNo, pageSize);
            }
            Collections.sort(allKeys);

            int from = Math.min((pageNo - 1) * pageSize, allKeys.size());
            int to = Math.min(from + pageSize, allKeys.size());
            List<RedisKeyVO> pageData = new ArrayList<>(to - from);
            for (String key : allKeys.subList(from, to)) {
                pageData.add(buildKeyVO(connection, key));
            }
            return WebPageResult.of(pageData, (long) allKeys.size(), pageNo, pageSize);
        } catch (Exception e) {
            log.error("Query redis key page failed", e);
            return WebPageResult.error("redis.query.error", e.getMessage());
        }
    }

    @GetMapping("/key/detail")
    public DataResult<RedisKeyDetailVO> detail(@Valid RedisKeyDetailRequest request) {
        Connection connection = Chat2DBContext.getConnection();
        if (connection == null) {
            return DataResult.error("redis.connection.error", "Redis connection is unavailable");
        }

        try {
            String keyName = request.getKeyName().trim();
            if (!exists(connection, keyName)) {
                return DataResult.error("redis.key.notFound", "Redis key not found: " + keyName);
            }

            String quotedKey = quoteToken(keyName);
            String keyType = StringUtils.lowerCase(defaultIfBlank(queryFirstValue(connection, "type " + quotedKey), TYPE_STRING));
            Long ttlSeconds = parseLong(queryFirstValue(connection, "ttl " + quotedKey));

            RedisKeyDetailVO.RedisKeyDetailVOBuilder builder = RedisKeyDetailVO.builder()
                    .keyName(keyName)
                    .keyType(keyType)
                    .ttlSeconds(ttlSeconds);

            switch (keyType) {
                case TYPE_HASH:
                    builder.hashValues(readHashValues(connection, quotedKey));
                    break;
                case TYPE_LIST:
                    builder.listValues(readListValues(connection, quotedKey));
                    break;
                case TYPE_SET:
                    builder.setValues(readSetValues(connection, quotedKey));
                    break;
                case TYPE_ZSET:
                    builder.zsetValues(readZSetValues(connection, quotedKey));
                    break;
                case TYPE_STREAM:
                    builder.streamValues(readStreamValues(connection, quotedKey));
                    break;
                case TYPE_STRING:
                default:
                    builder.stringValue(defaultIfBlank(queryFirstValue(connection, "get " + quotedKey), ""));
                    break;
            }

            return DataResult.of(builder.build());
        } catch (Exception e) {
            log.error("Query redis key detail failed", e);
            return DataResult.error("redis.key.detail.error", e.getMessage());
        }
    }

    @PostMapping("/key/save")
    public ActionResult save(@Valid @RequestBody RedisKeySaveRequest request) {
        Connection connection = Chat2DBContext.getConnection();
        if (connection == null) {
            return ActionResult.fail("redis.connection.error", "Redis connection is unavailable", null);
        }

        try {
            String targetKey = request.getKeyName().trim();
            String sourceKey = StringUtils.isBlank(request.getOriginalKeyName()) ? targetKey : request.getOriginalKeyName().trim();
            String targetType = StringUtils.lowerCase(request.getKeyType().trim());

            if (!sourceKey.equals(targetKey) && exists(connection, targetKey)) {
                SQLExecutor.getInstance().execute(connection, "del " + quoteToken(targetKey), rs -> null);
            }

            writeKeyByType(connection, targetKey, targetType, request);

            if (!sourceKey.equals(targetKey) && exists(connection, sourceKey)) {
                SQLExecutor.getInstance().execute(connection, "del " + quoteToken(sourceKey), rs -> null);
            }

            applyTtl(connection, targetKey, request.getTtlSeconds());
            return ActionResult.isSuccess();
        } catch (Exception e) {
            log.error("Save redis key failed", e);
            return ActionResult.fail("redis.key.save.error", e.getMessage(), null);
        }
    }

    @PostMapping("/key/create")
    public ActionResult create(@Valid @RequestBody RedisKeyCreateRequest request) {
        Connection connection = Chat2DBContext.getConnection();
        if (connection == null) {
            return ActionResult.fail("redis.connection.error", "Redis connection is unavailable", null);
        }

        try {
            String keyName = request.getKeyName().trim();
            if (Boolean.FALSE.equals(request.getOverwrite()) && exists(connection, keyName)) {
                return ActionResult.fail("redis.key.exists", "Redis key already exists: " + keyName, null);
            }

            RedisKeySaveRequest saveRequest = new RedisKeySaveRequest();
            saveRequest.setOriginalKeyName(keyName);
            saveRequest.setKeyName(keyName);
            saveRequest.setKeyType(TYPE_STRING);
            saveRequest.setStringValue(request.getValue());
            saveRequest.setTtlSeconds(request.getTtlSeconds());
            return save(saveRequest);
        } catch (Exception e) {
            log.error("Create redis key failed", e);
            return ActionResult.fail("redis.key.create.error", e.getMessage(), null);
        }
    }

    @PostMapping("/key/delete")
    public ActionResult delete(@Valid @RequestBody RedisKeyDeleteRequest request) {
        Connection connection = Chat2DBContext.getConnection();
        if (connection == null) {
            return ActionResult.fail("redis.connection.error", "Redis connection is unavailable", null);
        }

        try {
            for (String keyName : request.getKeyNames()) {
                if (StringUtils.isBlank(keyName)) {
                    continue;
                }
                SQLExecutor.getInstance().execute(connection, "del " + quoteToken(keyName.trim()), rs -> null);
            }
            return ActionResult.isSuccess();
        } catch (Exception e) {
            log.error("Delete redis keys failed", e);
            return ActionResult.fail("redis.key.delete.error", e.getMessage(), null);
        }
    }

    private void writeKeyByType(Connection connection, String keyName, String keyType, RedisKeySaveRequest request) {
        String quotedKey = quoteToken(keyName);

        switch (keyType) {
            case TYPE_STRING:
                SQLExecutor.getInstance().execute(connection,
                        "set " + quotedKey + " " + quoteToken(defaultIfBlank(request.getStringValue(), "")), rs -> null);
                break;
            case TYPE_HASH:
                SQLExecutor.getInstance().execute(connection, "del " + quotedKey, rs -> null);
                if (CollectionUtils.isNotEmpty(request.getHashValues())) {
                    for (RedisKeySaveRequest.FieldValue entry : request.getHashValues()) {
                        if (entry == null || StringUtils.isBlank(entry.getField())) {
                            continue;
                        }
                        SQLExecutor.getInstance().execute(connection,
                                "hset " + quotedKey + " " + quoteToken(entry.getField().trim()) + " "
                                        + quoteToken(defaultIfBlank(entry.getValue(), "")), rs -> null);
                    }
                }
                break;
            case TYPE_LIST:
                SQLExecutor.getInstance().execute(connection, "del " + quotedKey, rs -> null);
                if (CollectionUtils.isNotEmpty(request.getListValues())) {
                    StringBuilder sql = new StringBuilder("rpush ").append(quotedKey);
                    for (String value : request.getListValues()) {
                        sql.append(" ").append(quoteToken(defaultIfBlank(value, "")));
                    }
                    SQLExecutor.getInstance().execute(connection, sql.toString(), rs -> null);
                }
                break;
            case TYPE_SET:
                SQLExecutor.getInstance().execute(connection, "del " + quotedKey, rs -> null);
                if (CollectionUtils.isNotEmpty(request.getSetValues())) {
                    StringBuilder sql = new StringBuilder("sadd ").append(quotedKey);
                    for (String value : request.getSetValues()) {
                        sql.append(" ").append(quoteToken(defaultIfBlank(value, "")));
                    }
                    SQLExecutor.getInstance().execute(connection, sql.toString(), rs -> null);
                }
                break;
            case TYPE_ZSET:
                SQLExecutor.getInstance().execute(connection, "del " + quotedKey, rs -> null);
                if (CollectionUtils.isNotEmpty(request.getZsetValues())) {
                    for (RedisKeySaveRequest.ZSetValue item : request.getZsetValues()) {
                        if (item == null || StringUtils.isBlank(item.getMember())) {
                            continue;
                        }
                        String score = StringUtils.isBlank(item.getScore()) ? "0" : item.getScore().trim();
                        SQLExecutor.getInstance().execute(connection,
                                "zadd " + quotedKey + " " + score + " " + quoteToken(item.getMember().trim()), rs -> null);
                    }
                }
                break;
            case TYPE_STREAM:
                SQLExecutor.getInstance().execute(connection, "del " + quotedKey, rs -> null);
                if (CollectionUtils.isNotEmpty(request.getStreamValues())) {
                    for (RedisKeySaveRequest.StreamValue streamValue : request.getStreamValues()) {
                        if (streamValue == null || CollectionUtils.isEmpty(streamValue.getValues())) {
                            continue;
                        }
                        String entryId = StringUtils.isBlank(streamValue.getId()) ? "*" : streamValue.getId().trim();
                        StringBuilder sql = new StringBuilder("xadd ").append(quotedKey).append(" ").append(entryId);
                        for (RedisKeySaveRequest.FieldValue value : streamValue.getValues()) {
                            if (value == null || StringUtils.isBlank(value.getField())) {
                                continue;
                            }
                            sql.append(" ").append(quoteToken(value.getField().trim()))
                                    .append(" ").append(quoteToken(defaultIfBlank(value.getValue(), "")));
                        }
                        SQLExecutor.getInstance().execute(connection, sql.toString(), rs -> null);
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported redis key type: " + keyType);
        }
    }

    private void applyTtl(Connection connection, String keyName, Long ttlSeconds) {
        if (ttlSeconds == null) {
            return;
        }
        String quotedKey = quoteToken(keyName);
        if (ttlSeconds > 0) {
            SQLExecutor.getInstance().execute(connection, "expire " + quotedKey + " " + ttlSeconds, rs -> null);
        } else {
            SQLExecutor.getInstance().execute(connection, "persist " + quotedKey, rs -> null);
        }
    }

    private List<RedisKeyDetailVO.FieldValueVO> readHashValues(Connection connection, String quotedKey) {
        QueryResult result = queryResult(connection, "hgetall " + quotedKey);
        List<RedisKeyDetailVO.FieldValueVO> values = new ArrayList<>();
        for (List<String> row : result.rows) {
            if (CollectionUtils.isEmpty(row)) {
                continue;
            }
            if (row.size() >= 2) {
                values.add(RedisKeyDetailVO.FieldValueVO.builder()
                        .field(defaultIfBlank(row.get(0), ""))
                        .value(defaultIfBlank(row.get(1), ""))
                        .build());
            } else {
                List<String> flatten = expandCompositeValues(row);
                for (int i = 0; i + 1 < flatten.size(); i += 2) {
                    values.add(RedisKeyDetailVO.FieldValueVO.builder()
                            .field(flatten.get(i))
                            .value(flatten.get(i + 1))
                            .build());
                }
            }
        }
        return values;
    }

    private List<String> readListValues(Connection connection, String quotedKey) {
        QueryResult result = queryResult(connection, "lrange " + quotedKey + " 0 -1");
        List<String> values = new ArrayList<>();
        for (List<String> row : result.rows) {
            if (CollectionUtils.isEmpty(row)) {
                continue;
            }
            values.add(defaultIfBlank(row.get(row.size() - 1), ""));
        }
        return values;
    }

    private List<String> readSetValues(Connection connection, String quotedKey) {
        QueryResult result = queryResult(connection, "smembers " + quotedKey);
        List<String> values = new ArrayList<>();
        for (List<String> row : result.rows) {
            if (CollectionUtils.isEmpty(row)) {
                continue;
            }
            values.add(defaultIfBlank(row.get(row.size() - 1), ""));
        }
        return values;
    }

    private List<RedisKeyDetailVO.ZSetValueVO> readZSetValues(Connection connection, String quotedKey) {
        QueryResult result = queryResult(connection, "zscan " + quotedKey + " 0");
        List<String> flatten = new ArrayList<>();
        for (List<String> row : result.rows) {
            if (CollectionUtils.isEmpty(row)) {
                continue;
            }
            flatten.add(defaultIfBlank(row.get(row.size() - 1), ""));
        }
        flatten = expandCompositeValues(flatten);
        if (CollectionUtils.isNotEmpty(flatten) && flatten.size() % 2 == 1 && "0".equals(flatten.get(0))) {
            flatten = flatten.subList(1, flatten.size());
        }

        List<RedisKeyDetailVO.ZSetValueVO> values = new ArrayList<>();
        for (int i = 0; i + 1 < flatten.size(); i += 2) {
            values.add(RedisKeyDetailVO.ZSetValueVO.builder()
                    .member(flatten.get(i))
                    .score(flatten.get(i + 1))
                    .build());
        }
        return values;
    }

    private List<RedisKeyDetailVO.StreamValueVO> readStreamValues(Connection connection, String quotedKey) {
        QueryResult result = queryResult(connection, "xrange " + quotedKey + " - +");
        if (CollectionUtils.isEmpty(result.rows)) {
            return Collections.emptyList();
        }

        List<RedisKeyDetailVO.StreamValueVO> values = new ArrayList<>();
        if (result.headers.size() >= 2) {
            for (List<String> row : result.rows) {
                if (CollectionUtils.isEmpty(row)) {
                    continue;
                }
                String id = row.get(0);
                List<RedisKeyDetailVO.FieldValueVO> fieldValues = new ArrayList<>();
                for (int i = 1; i < Math.min(result.headers.size(), row.size()); i++) {
                    String fieldName = result.headers.get(i);
                    if (StringUtils.isBlank(fieldName) || "value".equalsIgnoreCase(fieldName)) {
                        continue;
                    }
                    fieldValues.add(RedisKeyDetailVO.FieldValueVO.builder()
                            .field(fieldName)
                            .value(defaultIfBlank(row.get(i), ""))
                            .build());
                }
                values.add(RedisKeyDetailVO.StreamValueVO.builder().id(id).values(fieldValues).build());
            }
            return values;
        }

        List<String> raws = queryValues(connection, "xrevrange " + quotedKey + " + - count 200");
        for (String raw : raws) {
            RedisKeyDetailVO.StreamValueVO streamValueVO = parseStreamCompositeRow(raw);
            if (streamValueVO != null) {
                values.add(streamValueVO);
            }
        }
        return values;
    }

    private RedisKeyDetailVO.StreamValueVO parseStreamCompositeRow(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String value = raw.trim();
        if (!value.startsWith("[") || value.length() < 3) {
            return null;
        }

        int firstComma = value.indexOf(',');
        if (firstComma < 0) {
            return null;
        }
        String id = value.substring(1, firstComma).trim();

        int innerStart = value.indexOf('[', firstComma);
        int innerEnd = value.lastIndexOf(']');
        if (innerStart < 0 || innerEnd <= innerStart) {
            return RedisKeyDetailVO.StreamValueVO.builder().id(id).values(Collections.emptyList()).build();
        }

        String content = value.substring(innerStart + 1, innerEnd).replace("]", "").trim();
        if (StringUtils.isBlank(content)) {
            return RedisKeyDetailVO.StreamValueVO.builder().id(id).values(Collections.emptyList()).build();
        }

        String[] parts = content.split(",");
        List<RedisKeyDetailVO.FieldValueVO> fieldValues = new ArrayList<>();
        for (int i = 0; i + 1 < parts.length; i += 2) {
            fieldValues.add(RedisKeyDetailVO.FieldValueVO.builder()
                    .field(parts[i].trim())
                    .value(parts[i + 1].trim())
                    .build());
        }

        return RedisKeyDetailVO.StreamValueVO.builder().id(id).values(fieldValues).build();
    }

    private RedisKeyVO buildKeyVO(Connection connection, String keyName) {
        String quotedKey = quoteToken(keyName);
        String keyType = StringUtils.lowerCase(defaultIfBlank(queryFirstValue(connection, "type " + quotedKey), TYPE_STRING));
        Long ttlSeconds = parseLong(queryFirstValue(connection, "ttl " + quotedKey));
        String preview = buildValuePreview(connection, quotedKey, keyType);
        return RedisKeyVO.builder()
                .keyName(keyName)
                .keyType(keyType)
                .valuePreview(preview)
                .ttlSeconds(ttlSeconds)
                .build();
    }

    private String buildValuePreview(Connection connection, String quotedKey, String keyType) {
        if (TYPE_STRING.equals(keyType)) {
            return shorten(defaultIfBlank(queryFirstValue(connection, "get " + quotedKey), ""));
        }
        if (TYPE_HASH.equals(keyType)) {
            return shorten(joinPreview(expandCompositeValues(queryValues(connection, "hgetall " + quotedKey))));
        }
        if (TYPE_LIST.equals(keyType)) {
            return shorten(joinPreview(expandCompositeValues(queryValues(connection, "lrange " + quotedKey + " 0 49"))));
        }
        if (TYPE_SET.equals(keyType)) {
            return shorten(joinPreview(expandCompositeValues(queryValues(connection, "smembers " + quotedKey))));
        }
        if (TYPE_ZSET.equals(keyType)) {
            return shorten(joinPreview(expandCompositeValues(queryValues(connection, "zscan " + quotedKey + " 0"))));
        }
        if (TYPE_STREAM.equals(keyType)) {
            return shorten(joinPreview(expandCompositeValues(queryValues(connection, "xrevrange " + quotedKey + " + - count 20"))));
        }
        return "";
    }

    private List<String> queryRedisKeys(Connection connection, String pattern) {
        List<String> raw = queryValues(connection, "keys " + quoteToken(pattern));
        List<String> keys = expandCompositeValues(raw);
        Set<String> deDuplicate = new LinkedHashSet<>();
        for (String key : keys) {
            if (StringUtils.isNotBlank(key)) {
                deDuplicate.add(key.trim());
            }
        }
        return new ArrayList<>(deDuplicate);
    }

    private boolean exists(Connection connection, String keyName) {
        Long exists = parseLong(queryFirstValue(connection, "exists " + quoteToken(keyName)));
        return exists != null && exists > 0;
    }

    private static String normalizeRedisPattern(String searchKey) {
        if (StringUtils.isBlank(searchKey)) {
            return "*";
        }
        String pattern = searchKey.trim().replace('%', '*');
        if (!pattern.contains("*") && !pattern.contains("?")) {
            pattern = "*" + pattern + "*";
        }
        return pattern;
    }

    private static String quoteToken(String token) {
        String escaped = defaultIfBlank(token, "").replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    private static String joinPreview(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return "";
        }
        int size = Math.min(values.size(), PREVIEW_LIST_LIMIT);
        String preview = String.join(", ", values.subList(0, size));
        if (values.size() > size) {
            preview += " ...";
        }
        return preview;
    }

    private static String shorten(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= PREVIEW_TEXT_LIMIT) {
            return value;
        }
        return value.substring(0, PREVIEW_TEXT_LIMIT) + "...";
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value;
    }

    private Long parseLong(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String queryFirstValue(Connection connection, String sql) {
        List<String> values = queryValues(connection, sql);
        return CollectionUtils.isEmpty(values) ? null : values.get(0);
    }

    private QueryResult queryResult(Connection connection, String sql) {
        QueryResult result = SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            QueryResult queryResult = new QueryResult();
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                queryResult.headers.add(defaultIfBlank(metaData.getColumnLabel(i), metaData.getColumnName(i)));
            }
            while (resultSet.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(defaultIfBlank(resultSet.getString(i), ""));
                }
                queryResult.rows.add(row);
            }
            return queryResult;
        });
        return result == null ? new QueryResult() : result;
    }

    private List<String> queryValues(Connection connection, String sql) {
        QueryResult queryResult = queryResult(connection, sql);
        List<String> values = new ArrayList<>();
        for (List<String> row : queryResult.rows) {
            for (String columnValue : row) {
                if (StringUtils.isNotBlank(columnValue)) {
                    values.add(columnValue.trim());
                }
            }
        }
        return values;
    }

    private static List<String> expandCompositeValues(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyList();
        }
        List<String> flatten = new ArrayList<>();
        for (String rawValue : values) {
            if (StringUtils.isBlank(rawValue)) {
                continue;
            }
            String value = rawValue.trim();
            if (value.startsWith("[") && value.endsWith("]")) {
                String content = value.substring(1, value.length() - 1).trim();
                if (StringUtils.isBlank(content)) {
                    continue;
                }
                for (String item : content.split(",")) {
                    if (StringUtils.isNotBlank(item)) {
                        flatten.add(item.trim());
                    }
                }
            } else {
                flatten.add(value);
            }
        }
        return flatten;
    }

    private static class QueryResult {
        private final List<String> headers = new ArrayList<>();
        private final List<List<String>> rows = new ArrayList<>();
    }
}

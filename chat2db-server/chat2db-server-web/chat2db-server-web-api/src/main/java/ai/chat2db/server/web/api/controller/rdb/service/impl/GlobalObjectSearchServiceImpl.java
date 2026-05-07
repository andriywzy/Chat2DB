package ai.chat2db.server.web.api.controller.rdb.service.impl;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.model.ObjectSearchIndexItem;
import ai.chat2db.server.domain.api.model.ObjectSearchSyncStatus;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.ObjectSearchIndexQueryService;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.web.api.controller.rdb.request.GlobalObjectSearchRequest;
import ai.chat2db.server.web.api.controller.rdb.service.GlobalObjectSearchService;
import ai.chat2db.server.web.api.controller.rdb.vo.GlobalObjectSearchItemVO;
import ai.chat2db.server.web.api.controller.rdb.vo.GlobalObjectSearchResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GlobalObjectSearchServiceImpl implements GlobalObjectSearchService {

    private static final int MAX_SCOPE_DATASOURCES = 1000;
    private static final String STATUS_FAILED = "FAILED";
    private static final String TYPE_TABLE = "table";
    private static final String TYPE_VIEW = "view";
    private static final String TYPE_FUNCTION = "function";
    private static final String TYPE_PROCEDURE = "procedure";
    private static final String TYPE_TRIGGER = "trigger";
    private static final List<String> ALL_TYPES = List.of(
        TYPE_TABLE,
        TYPE_VIEW,
        TYPE_FUNCTION,
        TYPE_PROCEDURE,
        TYPE_TRIGGER
    );

    private final DataSourceService dataSourceService;
    private final ObjectSearchIndexQueryService objectSearchIndexQueryService;

    public GlobalObjectSearchServiceImpl(
        DataSourceService dataSourceService,
        ObjectSearchIndexQueryService objectSearchIndexQueryService
    ) {
        this.dataSourceService = dataSourceService;
        this.objectSearchIndexQueryService = objectSearchIndexQueryService;
    }

    @Override
    public GlobalObjectSearchResponse search(GlobalObjectSearchRequest request) {
        List<DataSource> dataSources = loadAccessibleDataSources();
        if (CollectionUtils.isEmpty(dataSources)) {
            return emptyResponse();
        }

        Map<Long, DataSource> dataSourceMap = dataSources.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(DataSource::getId, item -> item, (left, right) -> right, LinkedHashMap::new));
        List<Long> dataSourceIds = new ArrayList<>(dataSourceMap.keySet());
        Map<Long, ObjectSearchSyncStatus> statusMap = objectSearchIndexQueryService.statusByDataSourceIds(dataSourceIds);
        List<ObjectSearchIndexItem> indexedItems = objectSearchIndexQueryService.listByDataSourceIds(dataSourceIds);

        List<String> requestTypes = normalizeTypes(request.getTypes());
        String keyword = StringUtils.trimToEmpty(request.getKeyword());
        List<GlobalObjectSearchItemVO> aggregated = indexedItems.stream()
            .map(item -> toItemVO(item, dataSourceMap.get(item.getDataSourceId())))
            .filter(Objects::nonNull)
            .filter(item -> matchesKeyword(item.getObjectName(), item.getComment(), keyword))
            .toList();

        List<GlobalObjectSearchItemVO> deduplicated = deduplicate(aggregated);
        Map<String, Long> countsByType = countByType(deduplicated);
        List<GlobalObjectSearchItemVO> sorted = sortResults(deduplicated, keyword);
        List<GlobalObjectSearchItemVO> filtered = filterByTypes(sorted, requestTypes);
        List<GlobalObjectSearchItemVO> paged = paginate(filtered, request.getPageNo(), request.getPageSize());

        WarningState warningState = buildWarnings(dataSources, statusMap);
        return GlobalObjectSearchResponse.builder()
            .data(paged)
            .total((long) filtered.size())
            .partial(warningState.partial())
            .warnings(warningState.warnings())
            .countsByType(countsByType)
            .build();
    }

    private List<DataSource> loadAccessibleDataSources() {
        DataSourcePageQueryParam param = new DataSourcePageQueryParam();
        param.setPageNo(1);
        param.setPageSize(MAX_SCOPE_DATASOURCES);
        PageResult<DataSource> pageResult = dataSourceService.queryPageWithPermission(
            param,
            DataSourceSelector.builder().environment(Boolean.TRUE).build()
        );
        return pageResult == null ? List.of() : pageResult.getData();
    }

    private WarningState buildWarnings(List<DataSource> dataSources, Map<Long, ObjectSearchSyncStatus> statusMap) {
        List<String> warnings = new ArrayList<>();
        boolean partial = false;
        for (DataSource dataSource : dataSources) {
            ObjectSearchSyncStatus status = statusMap.get(dataSource.getId());
            String alias = StringUtils.defaultIfBlank(dataSource.getAlias(), "DataSource " + dataSource.getId());
            if (status == null || status.getLastSyncVersion() == null) {
                partial = true;
                warnings.add(String.format("Datasource [%s] object index has not been synced yet", alias));
                continue;
            }
            if (STATUS_FAILED.equalsIgnoreCase(status.getLastSyncStatus())) {
                partial = true;
                warnings.add(String.format("Datasource [%s] object index sync failed recently", alias));
            }
        }
        return new WarningState(partial, warnings);
    }

    private GlobalObjectSearchItemVO toItemVO(ObjectSearchIndexItem item, DataSource dataSource) {
        if (item == null || dataSource == null || StringUtils.isBlank(item.getObjectName())) {
            return null;
        }
        return GlobalObjectSearchItemVO.builder()
            .dataSourceId(item.getDataSourceId())
            .dataSourceName(StringUtils.defaultIfBlank(dataSource.getAlias(), item.getDataSourceName()))
            .databaseType(StringUtils.defaultIfBlank(dataSource.getType(), item.getDatabaseType()))
            .supportDatabase(dataSource.isSupportDatabase())
            .supportSchema(dataSource.isSupportSchema())
            .databaseName(StringUtils.trimToNull(item.getDatabaseName()))
            .schemaName(StringUtils.trimToNull(item.getSchemaName()))
            .objectType(item.getObjectType())
            .objectName(StringUtils.trimToNull(item.getObjectName()))
            .comment(StringUtils.trimToNull(item.getComment()))
            .build();
    }

    private boolean matchesKeyword(String objectName, String comment, String keyword) {
        if (StringUtils.isBlank(objectName)) {
            return false;
        }
        if (StringUtils.isBlank(keyword)) {
            return true;
        }
        String normalizedKeyword = normalize(keyword);
        return normalize(objectName).contains(normalizedKeyword) || normalize(comment).contains(normalizedKeyword);
    }

    private String normalize(String value) {
        return StringUtils.defaultString(value).trim().toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeTypes(List<String> types) {
        if (CollectionUtils.isEmpty(types)) {
            return ALL_TYPES;
        }
        List<String> normalized = types.stream()
            .map(type -> StringUtils.trimToEmpty(type).toLowerCase(Locale.ROOT))
            .filter(ALL_TYPES::contains)
            .distinct()
            .toList();
        return normalized.isEmpty() ? ALL_TYPES : normalized;
    }

    private List<GlobalObjectSearchItemVO> deduplicate(List<GlobalObjectSearchItemVO> items) {
        Map<String, GlobalObjectSearchItemVO> deduplicated = new LinkedHashMap<>();
        for (GlobalObjectSearchItemVO item : items) {
            if (item == null || StringUtils.isBlank(item.getObjectName())) {
                continue;
            }
            String key = String.join("|",
                String.valueOf(item.getDataSourceId()),
                StringUtils.defaultString(item.getDatabaseName()),
                StringUtils.defaultString(item.getSchemaName()),
                StringUtils.defaultString(item.getObjectType()),
                StringUtils.defaultString(item.getObjectName())
            );
            GlobalObjectSearchItemVO existing = deduplicated.get(key);
            if (existing == null || StringUtils.isBlank(existing.getComment())) {
                deduplicated.put(key, item);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    private Map<String, Long> countByType(List<GlobalObjectSearchItemVO> items) {
        Map<String, Long> counts = ALL_TYPES.stream()
            .collect(Collectors.toMap(type -> type, type -> 0L, (left, right) -> left, LinkedHashMap::new));
        for (GlobalObjectSearchItemVO item : items) {
            counts.computeIfPresent(item.getObjectType(), (type, value) -> value + 1);
        }
        return counts;
    }

    private List<GlobalObjectSearchItemVO> sortResults(List<GlobalObjectSearchItemVO> items, String keyword) {
        return items.stream()
            .sorted(Comparator
                .comparingInt((GlobalObjectSearchItemVO item) -> matchScore(item, keyword)).reversed()
                .thenComparing(item -> StringUtils.defaultString(item.getDataSourceName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> StringUtils.defaultString(item.getDatabaseName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> StringUtils.defaultString(item.getSchemaName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> StringUtils.defaultString(item.getObjectName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> StringUtils.defaultString(item.getObjectType()), String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private int matchScore(GlobalObjectSearchItemVO item, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return 0;
        }
        String normalizedKeyword = normalize(keyword);
        String objectName = normalize(item.getObjectName());
        String comment = normalize(item.getComment());
        if (objectName.equals(normalizedKeyword)) {
            return 300;
        }
        if (objectName.startsWith(normalizedKeyword)) {
            return 200;
        }
        if (objectName.contains(normalizedKeyword)) {
            return 100;
        }
        if (comment.startsWith(normalizedKeyword)) {
            return 50;
        }
        if (comment.contains(normalizedKeyword)) {
            return 25;
        }
        return 0;
    }

    private List<GlobalObjectSearchItemVO> filterByTypes(List<GlobalObjectSearchItemVO> items, List<String> types) {
        return items.stream()
            .filter(item -> types.contains(item.getObjectType()))
            .toList();
    }

    private List<GlobalObjectSearchItemVO> paginate(List<GlobalObjectSearchItemVO> items, Integer pageNo, Integer pageSize) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : pageSize;
        int fromIndex = (safePageNo - 1) * safePageSize;
        if (fromIndex >= items.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return items.subList(fromIndex, toIndex);
    }

    private GlobalObjectSearchResponse emptyResponse() {
        return GlobalObjectSearchResponse.builder()
            .data(List.of())
            .total(0L)
            .partial(Boolean.FALSE)
            .warnings(List.of())
            .countsByType(ALL_TYPES.stream()
                .collect(Collectors.toMap(type -> type, type -> 0L, (left, right) -> left, LinkedHashMap::new)))
            .build();
    }

    private record WarningState(boolean partial, List<String> warnings) {
    }
}

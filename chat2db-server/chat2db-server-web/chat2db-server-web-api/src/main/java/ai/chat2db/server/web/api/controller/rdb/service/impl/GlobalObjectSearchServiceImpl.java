package ai.chat2db.server.web.api.controller.rdb.service.impl;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.SchemaQueryParam;
import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.param.datasource.DatabaseQueryAllParam;
import ai.chat2db.server.domain.api.service.*;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoHandler;
import ai.chat2db.server.web.api.controller.rdb.request.GlobalObjectSearchRequest;
import ai.chat2db.server.web.api.controller.rdb.service.GlobalObjectSearchService;
import ai.chat2db.server.web.api.controller.rdb.vo.GlobalObjectSearchItemVO;
import ai.chat2db.server.web.api.controller.rdb.vo.GlobalObjectSearchResponse;
import ai.chat2db.spi.model.*;
import ai.chat2db.spi.sql.Chat2DBContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GlobalObjectSearchServiceImpl implements GlobalObjectSearchService {

    private static final int MAX_SCOPE_DATASOURCES = 1000;
    private static final int SEARCH_CONCURRENCY = 8;
    private static final long SEARCH_TIMEOUT_SECONDS = 15L;
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
    private final DatabaseService databaseService;
    private final TableService tableService;
    private final ViewService viewService;
    private final FunctionService functionService;
    private final ProcedureService procedureService;
    private final TriggerService triggerService;
    private final ConnectionInfoHandler connectionInfoHandler;
    private final ExecutorService executorService = Executors.newFixedThreadPool(SEARCH_CONCURRENCY);

    public GlobalObjectSearchServiceImpl(
        DataSourceService dataSourceService,
        DatabaseService databaseService,
        TableService tableService,
        ViewService viewService,
        FunctionService functionService,
        ProcedureService procedureService,
        TriggerService triggerService,
        ConnectionInfoHandler connectionInfoHandler
    ) {
        this.dataSourceService = dataSourceService;
        this.databaseService = databaseService;
        this.tableService = tableService;
        this.viewService = viewService;
        this.functionService = functionService;
        this.procedureService = procedureService;
        this.triggerService = triggerService;
        this.connectionInfoHandler = connectionInfoHandler;
    }

    @Override
    public GlobalObjectSearchResponse search(GlobalObjectSearchRequest request) {
        List<DataSource> dataSources = loadAccessibleDataSources();
        if (CollectionUtils.isEmpty(dataSources)) {
            return emptyResponse();
        }

        List<String> requestTypes = normalizeTypes(request.getTypes());
        List<Callable<DataSourceSearchResult>> tasks = dataSources.stream()
            .map(dataSource -> (Callable<DataSourceSearchResult>) () -> searchInDataSource(
                dataSource,
                requestTypes,
                StringUtils.trimToEmpty(request.getKeyword()),
                Boolean.TRUE.equals(request.getRefresh())
            ))
            .toList();

        List<Future<DataSourceSearchResult>> futures;
        try {
            futures = executorService.invokeAll(tasks, SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return emptyResponse();
        }

        List<GlobalObjectSearchItemVO> aggregated = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean partial = false;

        for (int i = 0; i < futures.size(); i++) {
            Future<DataSourceSearchResult> future = futures.get(i);
            DataSource dataSource = dataSources.get(i);
            if (future.isCancelled()) {
                partial = true;
                warnings.add(String.format("Datasource [%s] search timeout", dataSource.getAlias()));
                continue;
            }
            try {
                DataSourceSearchResult result = future.get();
                if (result == null) {
                    continue;
                }
                aggregated.addAll(result.items());
                if (CollectionUtils.isNotEmpty(result.warnings())) {
                    partial = true;
                    warnings.addAll(result.warnings());
                }
            } catch (Exception exception) {
                partial = true;
                warnings.add(String.format("Datasource [%s] search failed", dataSource.getAlias()));
                log.debug("Global object search failed for datasource {}", dataSource.getId(), exception);
            }
        }

        List<GlobalObjectSearchItemVO> deduplicated = deduplicate(aggregated);
        Map<String, Long> countsByType = countByType(deduplicated);
        List<GlobalObjectSearchItemVO> sorted = sortResults(deduplicated, StringUtils.trimToEmpty(request.getKeyword()));
        List<GlobalObjectSearchItemVO> filtered = filterByTypes(sorted, requestTypes);
        List<GlobalObjectSearchItemVO> paged = paginate(filtered, request.getPageNo(), request.getPageSize());

        return GlobalObjectSearchResponse.builder()
            .data(paged)
            .total((long) filtered.size())
            .partial(partial)
            .warnings(warnings)
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

    private DataSourceSearchResult searchInDataSource(
        DataSource dataSource,
        List<String> requestTypes,
        String keyword,
        boolean refresh
    ) {
        List<GlobalObjectSearchItemVO> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> databases;
        try {
            databases = resolveDatabases(dataSource, refresh);
        } catch (Exception exception) {
            log.debug("Resolve databases failed for datasource {}", dataSource.getId(), exception);
            return new DataSourceSearchResult(
                List.of(),
                List.of(String.format("Datasource [%s] database enumeration failed", dataSource.getAlias()))
            );
        }

        for (String databaseName : databases) {
            List<String> schemas;
            try {
                schemas = resolveSchemas(dataSource, databaseName, refresh);
            } catch (Exception exception) {
                log.debug("Resolve schemas failed for datasource {}", dataSource.getId(), exception);
                warnings.add(String.format(
                    "Datasource [%s] schema enumeration failed for database [%s]",
                    dataSource.getAlias(),
                    StringUtils.defaultIfBlank(databaseName, "-")
                ));
                continue;
            }
            for (String schemaName : schemas) {
                try {
                    items.addAll(searchInScope(dataSource, databaseName, schemaName, requestTypes, keyword, refresh));
                } catch (Exception exception) {
                    log.debug(
                        "Search scope failed for datasource {}, database {}, schema {}",
                        dataSource.getId(),
                        databaseName,
                        schemaName,
                        exception
                    );
                    warnings.add(String.format(
                        "Datasource [%s] scope search failed for [%s/%s]",
                        dataSource.getAlias(),
                        StringUtils.defaultIfBlank(databaseName, "-"),
                        StringUtils.defaultIfBlank(schemaName, "-")
                    ));
                }
            }
        }
        return new DataSourceSearchResult(items, warnings);
    }

    private List<String> resolveDatabases(DataSource dataSource, boolean refresh) {
        if (!dataSource.isSupportDatabase()) {
            return Collections.singletonList(null);
        }
        List<Database> databases = runWithContext(dataSource, null, null, () ->
            databaseService.queryAll(DatabaseQueryAllParam.builder()
                .dataSourceId(dataSource.getId())
                .refresh(refresh)
                .dbType(dataSource.getType())
                .build())
                .getData()
        );
        List<String> names = Optional.ofNullable(databases).orElse(List.of()).stream()
            .map(Database::getName)
            .map(StringUtils::trimToNull)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        return names.isEmpty() ? Collections.singletonList(null) : names;
    }

    private List<String> resolveSchemas(DataSource dataSource, String databaseName, boolean refresh) {
        if (!dataSource.isSupportSchema()) {
            return Collections.singletonList(null);
        }
        List<Schema> schemas = runWithContext(dataSource, databaseName, null, () ->
            databaseService.querySchema(SchemaQueryParam.builder()
                .dataSourceId(dataSource.getId())
                .dataBaseName(databaseName)
                .refresh(refresh)
                .build())
                .getData()
        );
        List<String> names = Optional.ofNullable(schemas).orElse(List.of()).stream()
            .map(Schema::getName)
            .map(StringUtils::trimToNull)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        return names.isEmpty() ? Collections.singletonList(null) : names;
    }

    private List<GlobalObjectSearchItemVO> searchInScope(
        DataSource dataSource,
        String databaseName,
        String schemaName,
        List<String> requestTypes,
        String keyword,
        boolean refresh
    ) {
        return runWithContext(dataSource, databaseName, schemaName, () -> {
            List<GlobalObjectSearchItemVO> items = new ArrayList<>();
            if (requestTypes.contains(TYPE_TABLE)) {
                List<SimpleTable> tables = Optional.ofNullable(tableService.queryTables(TablePageQueryParam.builder()
                    .dataSourceId(dataSource.getId())
                    .databaseName(databaseName)
                    .schemaName(schemaName)
                    .refresh(refresh)
                    .build()))
                    .map(ListResult::getData)
                    .orElse(List.of());
                for (SimpleTable table : tables) {
                    String objectName = StringUtils.trimToNull(table.getName());
                    if (!matchesKeyword(objectName, table.getComment(), keyword)) {
                        continue;
                    }
                    items.add(buildItem(dataSource, databaseName, schemaName, TYPE_TABLE, objectName, table.getComment()));
                }
            }
            if (requestTypes.contains(TYPE_VIEW)) {
                List<Table> views = Optional.ofNullable(viewService.views(databaseName, schemaName))
                    .map(ListResult::getData)
                    .orElse(List.of());
                for (Table view : views) {
                    String objectName = StringUtils.trimToNull(view.getName());
                    if (!matchesKeyword(objectName, view.getComment(), keyword)) {
                        continue;
                    }
                    items.add(buildItem(
                        dataSource,
                        StringUtils.defaultIfBlank(view.getDatabaseName(), databaseName),
                        StringUtils.defaultIfBlank(view.getSchemaName(), schemaName),
                        TYPE_VIEW,
                        objectName,
                        view.getComment()
                    ));
                }
            }
            if (requestTypes.contains(TYPE_FUNCTION)) {
                List<Function> functions = Optional.ofNullable(functionService.functions(databaseName, schemaName))
                    .map(ListResult::getData)
                    .orElse(List.of());
                for (Function function : functions) {
                    String objectName = StringUtils.trimToNull(function.getFunctionName());
                    if (!matchesKeyword(objectName, function.getRemarks(), keyword)) {
                        continue;
                    }
                    items.add(buildItem(
                        dataSource,
                        StringUtils.defaultIfBlank(function.getDatabaseName(), databaseName),
                        StringUtils.defaultIfBlank(function.getSchemaName(), schemaName),
                        TYPE_FUNCTION,
                        objectName,
                        function.getRemarks()
                    ));
                }
            }
            if (requestTypes.contains(TYPE_PROCEDURE)) {
                List<Procedure> procedures = Optional.ofNullable(procedureService.procedures(databaseName, schemaName))
                    .map(ListResult::getData)
                    .orElse(List.of());
                for (Procedure procedure : procedures) {
                    String objectName = StringUtils.trimToNull(procedure.getProcedureName());
                    if (!matchesKeyword(objectName, procedure.getRemarks(), keyword)) {
                        continue;
                    }
                    items.add(buildItem(
                        dataSource,
                        StringUtils.defaultIfBlank(procedure.getDatabaseName(), databaseName),
                        StringUtils.defaultIfBlank(procedure.getSchemaName(), schemaName),
                        TYPE_PROCEDURE,
                        objectName,
                        procedure.getRemarks()
                    ));
                }
            }
            if (requestTypes.contains(TYPE_TRIGGER)) {
                List<Trigger> triggers = Optional.ofNullable(triggerService.triggers(databaseName, schemaName))
                    .map(ListResult::getData)
                    .orElse(List.of());
                for (Trigger trigger : triggers) {
                    String objectName = StringUtils.trimToNull(trigger.getTriggerName());
                    if (!matchesKeyword(objectName, null, keyword)) {
                        continue;
                    }
                    items.add(buildItem(
                        dataSource,
                        StringUtils.defaultIfBlank(trigger.getDatabaseName(), databaseName),
                        StringUtils.defaultIfBlank(trigger.getSchemaName(), schemaName),
                        TYPE_TRIGGER,
                        objectName,
                        null
                    ));
                }
            }
            return items;
        });
    }

    private GlobalObjectSearchItemVO buildItem(
        DataSource dataSource,
        String databaseName,
        String schemaName,
        String objectType,
        String objectName,
        String comment
    ) {
        return GlobalObjectSearchItemVO.builder()
            .dataSourceId(dataSource.getId())
            .dataSourceName(StringUtils.defaultIfBlank(dataSource.getAlias(), "DataSource " + dataSource.getId()))
            .databaseType(dataSource.getType())
            .supportDatabase(dataSource.isSupportDatabase())
            .supportSchema(dataSource.isSupportSchema())
            .databaseName(StringUtils.trimToNull(databaseName))
            .schemaName(StringUtils.trimToNull(schemaName))
            .objectType(objectType)
            .objectName(StringUtils.trimToNull(objectName))
            .comment(StringUtils.trimToNull(comment))
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

    private <T> T runWithContext(DataSource dataSource, String databaseName, String schemaName, ContextSupplier<T> supplier) {
        try {
            Chat2DBContext.putContext(connectionInfoHandler.toInfo(dataSource.getId(), databaseName, null, schemaName));
            return supplier.get();
        } finally {
            Chat2DBContext.removeContext();
        }
    }

    @FunctionalInterface
    private interface ContextSupplier<T> {
        T get();
    }

    private record DataSourceSearchResult(List<GlobalObjectSearchItemVO> items, List<String> warnings) {
    }
}

package ai.chat2db.server.web.api.controller.ai.platform.retrieval;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.ShowCreateTableParam;
import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.param.datasource.DatabaseQueryAllParam;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoHandler;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalContext;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalQuery;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiSchemaSource;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.SimpleTable;
import ai.chat2db.spi.sql.Chat2DBContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DefaultProjectSchemaContextService implements ProjectSchemaContextService {

    private static final int MAX_SCOPE_DATASOURCES = 300;
    private static final int MAX_CURRENT_DDL_TABLES = 4;
    private static final int MAX_PEER_HINT_TABLES = 6;
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[a-z0-9_]{2,}");
    private static final Pattern CJK_TOKEN_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]{2,}");

    private final DataSourceService dataSourceService;
    private final DatabaseService databaseService;
    private final TableService tableService;
    private final ConnectionInfoHandler connectionInfoHandler;
    private final Map<String, CachedScopeTables> cache = new ConcurrentHashMap<>();

    public DefaultProjectSchemaContextService(
        DataSourceService dataSourceService,
        DatabaseService databaseService,
        TableService tableService,
        ConnectionInfoHandler connectionInfoHandler
    ) {
        this.dataSourceService = dataSourceService;
        this.databaseService = databaseService;
        this.tableService = tableService;
        this.connectionInfoHandler = connectionInfoHandler;
    }

    @Override
    public AiRetrievalContext retrieveSchema(AiRetrievalQuery query) {
        if (query == null || query.getDataSourceId() == null || StringUtils.isBlank(query.getMessage())) {
            return AiRetrievalContext.builder().schemaSnippets(List.of()).schemaSources(List.of()).build();
        }
        DataResult<DataSource> currentResult = dataSourceService.queryById(query.getDataSourceId());
        DataSource currentDataSource = currentResult == null ? null : currentResult.getData();
        if (currentDataSource == null) {
            return AiRetrievalContext.builder().schemaSnippets(List.of()).schemaSources(List.of()).build();
        }

        ScopeDefinition scopeDefinition = resolveScope(currentDataSource, query);
        List<ProjectTableCandidate> candidates = loadScopeTables(scopeDefinition, query);
        if (CollectionUtils.isEmpty(candidates)) {
            return AiRetrievalContext.builder().schemaSnippets(List.of()).schemaSources(List.of()).build();
        }

        List<ProjectTableCandidate> currentMatches = rankCandidates(candidates, query.getMessage(), true).stream()
            .limit(MAX_CURRENT_DDL_TABLES)
            .toList();
        List<ProjectTableCandidate> peerMatches = rankCandidates(candidates, query.getMessage(), false).stream()
            .limit(MAX_PEER_HINT_TABLES)
            .toList();

        List<String> snippets = new ArrayList<>();
        List<AiSchemaSource> schemaSources = new ArrayList<>();
        Set<String> keywords = extractKeywords(query.getMessage());
        for (ProjectTableCandidate candidate : currentMatches) {
            String ddl = queryCurrentTableDdl(candidate, query);
            if (StringUtils.isNotBlank(ddl)) {
                snippets.add(String.format(
                    "[CURRENT_DATASOURCE_DDL][table=%s][database=%s]%n%s",
                    candidate.tableName(),
                    StringUtils.defaultIfBlank(candidate.databaseName(), query.getDatabaseName()),
                    ddl
                ));
                schemaSources.add(toSchemaSource(candidate, "CURRENT_RELATED", scoreCandidate(candidate, keywords)));
            }
        }

        if (CollectionUtils.isNotEmpty(peerMatches)) {
            snippets.add(buildPeerHintSnippet(peerMatches));
            for (ProjectTableCandidate candidate : peerMatches) {
                schemaSources.add(toSchemaSource(candidate, "PROJECT_HINT", scoreCandidate(candidate, keywords)));
            }
        }
        return AiRetrievalContext.builder()
            .schemaSnippets(snippets)
            .schemaSources(deduplicateSources(schemaSources))
            .build();
    }

    private ScopeDefinition resolveScope(DataSource currentDataSource, AiRetrievalQuery query) {
        Long projectId = currentDataSource.getProjectId();
        Long environmentId = currentDataSource.getEnvironmentId();
        String dbType = StringUtils.defaultIfBlank(currentDataSource.getType(), "MYSQL");
        return new ScopeDefinition(
            currentDataSource.getId(),
            projectId,
            environmentId,
            dbType,
            StringUtils.defaultString(query.getDatabaseName()),
            StringUtils.defaultString(query.getSchemaName())
        );
    }

    private List<ProjectTableCandidate> loadScopeTables(ScopeDefinition scopeDefinition, AiRetrievalQuery query) {
        String cacheKey = buildCacheKey(scopeDefinition);
        CachedScopeTables cached = cache.get(cacheKey);
        if (!Boolean.TRUE.equals(query.getRefresh())
            && cached != null
            && cached.expireAtMillis() > System.currentTimeMillis()) {
            return cached.tables();
        }

        List<DataSource> scopedDataSources = resolveScopedDataSources(scopeDefinition);
        if (CollectionUtils.isEmpty(scopedDataSources)) {
            return List.of();
        }

        List<ProjectTableCandidate> candidates = new ArrayList<>();
        for (DataSource dataSource : scopedDataSources) {
            candidates.addAll(loadTableCandidates(dataSource, scopeDefinition, Boolean.TRUE.equals(query.getRefresh())));
        }
        cache.put(cacheKey, new CachedScopeTables(System.currentTimeMillis() + CACHE_TTL_MILLIS, candidates));
        return candidates;
    }

    private List<DataSource> resolveScopedDataSources(ScopeDefinition scopeDefinition) {
        DataSourcePageQueryParam param = new DataSourcePageQueryParam();
        param.setPageNo(1);
        param.setPageSize(MAX_SCOPE_DATASOURCES);
        PageResult<DataSource> page = dataSourceService.queryPageWithPermission(
            param,
            DataSourceSelector.builder().environment(Boolean.TRUE).build()
        );
        List<DataSource> records = page == null ? List.of() : page.getData();
        if (CollectionUtils.isEmpty(records)) {
            return List.of();
        }

        List<DataSource> scoped = new ArrayList<>();
        for (DataSource dataSource : records) {
            if (!Objects.equals(scopeDefinition.projectId(), dataSource.getProjectId())) {
                continue;
            }
            if (!Objects.equals(scopeDefinition.environmentId(), dataSource.getEnvironmentId())) {
                continue;
            }
            if (!StringUtils.equalsIgnoreCase(scopeDefinition.dbType(), dataSource.getType())) {
                continue;
            }
            scoped.add(dataSource);
        }
        if (CollectionUtils.isEmpty(scoped)) {
            scoped.add(records.stream()
                .filter(dataSource -> Objects.equals(dataSource.getId(), scopeDefinition.currentDataSourceId()))
                .findFirst()
                .orElse(null));
            scoped.removeIf(Objects::isNull);
        }
        return scoped;
    }

    private List<ProjectTableCandidate> loadTableCandidates(DataSource dataSource, ScopeDefinition scopeDefinition, boolean refresh) {
        String databaseName = resolveDatabaseName(dataSource, scopeDefinition, refresh);
        if (StringUtils.isBlank(databaseName)) {
            return List.of();
        }
        String schemaName = Objects.equals(dataSource.getId(), scopeDefinition.currentDataSourceId())
            ? scopeDefinition.schemaName()
            : null;

        final String initialDatabaseName = databaseName;
        final String initialSchemaName = schemaName;
        List<SimpleTable> tables = runWithContext(dataSource, initialDatabaseName, initialSchemaName, () ->
            tableService.queryTables(TablePageQueryParam.builder()
                .dataSourceId(dataSource.getId())
                .databaseName(initialDatabaseName)
                .schemaName(initialSchemaName)
                .refresh(refresh)
                .build())
                .getData()
        );
        if (CollectionUtils.isEmpty(tables) && !Objects.equals(dataSource.getId(), scopeDefinition.currentDataSourceId())) {
            String fallbackDatabaseName = resolveFallbackDatabaseName(dataSource, refresh);
            if (StringUtils.isNotBlank(fallbackDatabaseName) && !StringUtils.equals(databaseName, fallbackDatabaseName)) {
                databaseName = fallbackDatabaseName;
                final String retryDatabaseName = databaseName;
                tables = runWithContext(dataSource, retryDatabaseName, null, () ->
                    tableService.queryTables(TablePageQueryParam.builder()
                        .dataSourceId(dataSource.getId())
                        .databaseName(retryDatabaseName)
                        .refresh(refresh)
                        .build())
                        .getData()
                );
                schemaName = null;
            }
        }
        if (CollectionUtils.isEmpty(tables)) {
            return List.of();
        }

        List<ProjectTableCandidate> candidates = new ArrayList<>();
        for (SimpleTable table : tables) {
            if (StringUtils.isBlank(table.getName())) {
                continue;
            }
            candidates.add(new ProjectTableCandidate(
                dataSource.getId(),
                StringUtils.defaultIfBlank(dataSource.getAlias(), "DataSource " + dataSource.getId()),
                databaseName,
                schemaName,
                table.getName(),
                table.getComment(),
                Objects.equals(dataSource.getId(), scopeDefinition.currentDataSourceId())
            ));
        }
        return candidates;
    }

    private String resolveDatabaseName(DataSource dataSource, ScopeDefinition scopeDefinition, boolean refresh) {
        if (Objects.equals(dataSource.getId(), scopeDefinition.currentDataSourceId())
            && StringUtils.isNotBlank(scopeDefinition.databaseName())) {
            return scopeDefinition.databaseName();
        }
        if (StringUtils.isNotBlank(scopeDefinition.databaseName())) {
            return scopeDefinition.databaseName();
        }
        return resolveFallbackDatabaseName(dataSource, refresh);
    }

    private String resolveFallbackDatabaseName(DataSource dataSource, boolean refresh) {
        List<Database> databases = runWithContext(dataSource, null, null, () ->
            databaseService.queryAll(DatabaseQueryAllParam.builder()
                .dataSourceId(dataSource.getId())
                .refresh(refresh)
                .dbType(dataSource.getType())
                .build())
                .getData()
        );
        if (CollectionUtils.isEmpty(databases)) {
            return null;
        }
        return databases.stream()
            .map(Database::getName)
            .filter(StringUtils::isNotBlank)
            .findFirst()
            .orElse(null);
    }

    private List<ProjectTableCandidate> rankCandidates(List<ProjectTableCandidate> candidates, String message, boolean currentOnly) {
        Set<String> keywords = extractKeywords(message);
        return candidates.stream()
            .filter(candidate -> candidate.currentDataSource() == currentOnly)
            .sorted(Comparator.comparingDouble((ProjectTableCandidate candidate) -> scoreCandidate(candidate, keywords)).reversed())
            .toList();
    }

    private double scoreCandidate(ProjectTableCandidate candidate, Set<String> keywords) {
        String normalizedTableName = normalize(candidate.tableName());
        String normalizedComment = normalize(candidate.comment());
        double score = candidate.currentDataSource() ? 12 : 4;
        for (String keyword : keywords) {
            String normalizedKeyword = normalize(keyword);
            if (StringUtils.isBlank(normalizedKeyword)) {
                continue;
            }
            if (normalizedTableName.contains(normalizedKeyword)) {
                score += 8;
            }
            if (normalizedComment.contains(normalizedKeyword)) {
                score += 4;
            }
        }
        if (StringUtils.isBlank(candidate.comment())) {
            score -= 1;
        }
        return score;
    }

    private Set<String> extractKeywords(String message) {
        Set<String> keywords = new LinkedHashSet<>();
        if (StringUtils.isBlank(message)) {
            return keywords;
        }
        Matcher englishMatcher = ENGLISH_TOKEN_PATTERN.matcher(message.toLowerCase());
        while (englishMatcher.find()) {
            keywords.add(englishMatcher.group());
        }
        Matcher cjkMatcher = CJK_TOKEN_PATTERN.matcher(message);
        while (cjkMatcher.find()) {
            keywords.add(cjkMatcher.group());
        }
        return keywords;
    }

    private String normalize(String value) {
        if (StringUtils.isBlank(value)) {
            return StringUtils.EMPTY;
        }
        return value.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fff]", StringUtils.EMPTY);
    }

    private String queryCurrentTableDdl(ProjectTableCandidate candidate, AiRetrievalQuery query) {
        return runWithContext(candidate, () ->
            tableService.showCreateTable(ShowCreateTableParam.builder()
                .dataSourceId(candidate.dataSourceId())
                .databaseName(StringUtils.defaultIfBlank(query.getDatabaseName(), candidate.databaseName()))
                .schemaName(StringUtils.defaultIfBlank(query.getSchemaName(), candidate.schemaName()))
                .tableName(candidate.tableName())
                .build())
                .getData()
        );
    }

    private String buildPeerHintSnippet(List<ProjectTableCandidate> peerMatches) {
        Map<String, List<ProjectTableCandidate>> grouped = new LinkedHashMap<>();
        for (ProjectTableCandidate candidate : peerMatches) {
            String key = candidate.dataSourceAlias() + "|" + StringUtils.defaultIfBlank(candidate.databaseName(), "-");
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[PROJECT_PEER_SCHEMA_HINT]");
        builder.append("[same_project_same_environment_same_db_type]");
        builder.append("[for_table_intent_matching_only_not_execution_target]").append('\n');
        for (Map.Entry<String, List<ProjectTableCandidate>> entry : grouped.entrySet()) {
            ProjectTableCandidate head = entry.getValue().get(0);
            builder.append("- datasource=")
                .append(head.dataSourceAlias())
                .append(", database=")
                .append(StringUtils.defaultIfBlank(head.databaseName(), "-"))
                .append(", tables=");
            List<String> tableTexts = new ArrayList<>();
            for (ProjectTableCandidate candidate : entry.getValue()) {
                tableTexts.add(StringUtils.isNotBlank(candidate.comment())
                    ? candidate.tableName() + "(" + candidate.comment() + ")"
                    : candidate.tableName());
            }
            builder.append(String.join(", ", tableTexts)).append('\n');
        }
        return builder.toString().trim();
    }

    private List<AiSchemaSource> deduplicateSources(List<AiSchemaSource> schemaSources) {
        Map<String, AiSchemaSource> sourceMap = new LinkedHashMap<>();
        for (AiSchemaSource schemaSource : schemaSources) {
            if (schemaSource == null || StringUtils.isBlank(schemaSource.getTableName())) {
                continue;
            }
            String key = String.join("|",
                StringUtils.defaultString(schemaSource.getSourceType()),
                StringUtils.defaultString(schemaSource.getDatabaseName()),
                StringUtils.defaultString(schemaSource.getSchemaName()),
                StringUtils.defaultString(schemaSource.getTableName()),
                String.valueOf(schemaSource.getDataSourceId())
            );
            sourceMap.putIfAbsent(key, schemaSource);
        }
        return new ArrayList<>(sourceMap.values());
    }

    private AiSchemaSource toSchemaSource(ProjectTableCandidate candidate, String sourceType, double score) {
        return AiSchemaSource.builder()
            .dataSourceId(candidate.dataSourceId())
            .dataSourceAlias(candidate.dataSourceAlias())
            .databaseName(candidate.databaseName())
            .schemaName(candidate.schemaName())
            .tableName(candidate.tableName())
            .currentDataSource(candidate.currentDataSource())
            .sourceType(sourceType)
            .score(score)
            .build();
    }

    private String buildCacheKey(ScopeDefinition scopeDefinition) {
        return String.format(
            "%s:%s:%s:%s:%s",
            scopeDefinition.projectId(),
            scopeDefinition.environmentId(),
            scopeDefinition.dbType(),
            StringUtils.defaultString(scopeDefinition.databaseName()),
            StringUtils.defaultString(scopeDefinition.schemaName())
        );
    }

    private <T> T runWithContext(ProjectTableCandidate candidate, ContextSupplier<T> supplier) {
        DataResult<DataSource> result = dataSourceService.queryById(candidate.dataSourceId());
        if (result == null || result.getData() == null) {
            return null;
        }
        return runWithContext(result.getData(), candidate.databaseName(), candidate.schemaName(), supplier);
    }

    private <T> T runWithContext(DataSource dataSource, String databaseName, String schemaName, ContextSupplier<T> supplier) {
        try {
            Chat2DBContext.putContext(connectionInfoHandler.toInfo(dataSource.getId(), databaseName, null, schemaName));
            return supplier.get();
        } catch (Exception exception) {
            log.debug("Failed to load project schema context for datasource {}", dataSource.getId(), exception);
            return null;
        } finally {
            Chat2DBContext.removeContext();
        }
    }

    @FunctionalInterface
    private interface ContextSupplier<T> {
        T get();
    }

    private record CachedScopeTables(long expireAtMillis, List<ProjectTableCandidate> tables) {
    }

    private record ScopeDefinition(
        Long currentDataSourceId,
        Long projectId,
        Long environmentId,
        String dbType,
        String databaseName,
        String schemaName
    ) {
    }

    private record ProjectTableCandidate(
        Long dataSourceId,
        String dataSourceAlias,
        String databaseName,
        String schemaName,
        String tableName,
        String comment,
        boolean currentDataSource
    ) {
    }
}

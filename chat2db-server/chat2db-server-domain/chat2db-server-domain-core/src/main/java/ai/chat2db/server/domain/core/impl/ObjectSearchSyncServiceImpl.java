package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.param.SchemaQueryParam;
import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DatabaseQueryAllParam;
import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.FunctionService;
import ai.chat2db.server.domain.api.service.ObjectSearchSyncService;
import ai.chat2db.server.domain.api.service.ProcedureService;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.api.service.TriggerService;
import ai.chat2db.server.domain.api.service.ViewService;
import ai.chat2db.server.domain.core.converter.DataSourceConverter;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.DataSourceDO;
import ai.chat2db.server.domain.repository.entity.ObjectSearchIndexDO;
import ai.chat2db.server.domain.repository.entity.ObjectSearchSyncStatusDO;
import ai.chat2db.server.domain.repository.mapper.DataSourceMapper;
import ai.chat2db.server.domain.repository.mapper.ObjectSearchIndexMapper;
import ai.chat2db.server.domain.repository.mapper.ObjectSearchSyncStatusMapper;
import ai.chat2db.server.tools.base.enums.DataSourceTypeEnum;
import ai.chat2db.spi.config.DBConfig;
import ai.chat2db.spi.config.DriverConfig;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Function;
import ai.chat2db.spi.model.Procedure;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.SimpleTable;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.Trigger;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.util.RedisJdbcUrlUtils;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ObjectSearchSyncServiceImpl implements ObjectSearchSyncService {

    private static final int SYNC_CONCURRENCY = 4;
    private static final long SYNC_INTERVAL_MILLIS = 5 * 60 * 1000L;
    private static final int INSERT_BATCH_SIZE = 500;
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String TYPE_TABLE = "table";
    private static final String TYPE_VIEW = "view";
    private static final String TYPE_FUNCTION = "function";
    private static final String TYPE_PROCEDURE = "procedure";
    private static final String TYPE_TRIGGER = "trigger";
    private static final int DATA_SOURCE_NAME_MAX_LENGTH = 256;
    private static final int DATABASE_TYPE_MAX_LENGTH = 64;
    private static final int DATABASE_NAME_MAX_LENGTH = 256;
    private static final int SCHEMA_NAME_MAX_LENGTH = 256;
    private static final int OBJECT_TYPE_MAX_LENGTH = 32;
    private static final int OBJECT_NAME_MAX_LENGTH = 512;
    private static final int COMMENT_MAX_LENGTH = 2048;
    private static final int LAST_SYNC_ERROR_MAX_LENGTH = 2048;
    private static final Set<String> UNSUPPORTED_OBJECT_SEARCH_TYPES = Set.of(
        DataSourceTypeEnum.REDIS.getCode(),
        DataSourceTypeEnum.MONGODB.getCode()
    );

    private final DatabaseService databaseService;
    private final TableService tableService;
    private final ViewService viewService;
    private final FunctionService functionService;
    private final ProcedureService procedureService;
    private final TriggerService triggerService;
    private final DataSourceConverter dataSourceConverter;
    private final ExecutorService executorService = Executors.newFixedThreadPool(SYNC_CONCURRENCY);
    private final Set<Long> runningDataSourceIds = ConcurrentHashMap.newKeySet();

    public ObjectSearchSyncServiceImpl(
        DatabaseService databaseService,
        TableService tableService,
        ViewService viewService,
        FunctionService functionService,
        ProcedureService procedureService,
        TriggerService triggerService,
        DataSourceConverter dataSourceConverter
    ) {
        this.databaseService = databaseService;
        this.tableService = tableService;
        this.viewService = viewService;
        this.functionService = functionService;
        this.procedureService = procedureService;
        this.triggerService = triggerService;
        this.dataSourceConverter = dataSourceConverter;
    }

    private DataSourceMapper getDataSourceMapper() {
        return Dbutils.getMapper(DataSourceMapper.class);
    }

    private ObjectSearchIndexMapper getIndexMapper() {
        return Dbutils.getMapper(ObjectSearchIndexMapper.class);
    }

    private ObjectSearchSyncStatusMapper getStatusMapper() {
        return Dbutils.getMapper(ObjectSearchSyncStatusMapper.class);
    }

    @Override
    public void requestSync(Long dataSourceId) {
        submitSync(dataSourceId, true);
    }

    @Override
    public void removeDataSource(Long dataSourceId) {
        if (dataSourceId == null) {
            return;
        }
        runningDataSourceIds.remove(dataSourceId);
        LambdaQueryWrapper<ObjectSearchIndexDO> indexWrapper = new LambdaQueryWrapper<>();
        indexWrapper.eq(ObjectSearchIndexDO::getDataSourceId, dataSourceId);
        getIndexMapper().delete(indexWrapper);

        LambdaQueryWrapper<ObjectSearchSyncStatusDO> statusWrapper = new LambdaQueryWrapper<>();
        statusWrapper.eq(ObjectSearchSyncStatusDO::getDataSourceId, dataSourceId);
        getStatusMapper().delete(statusWrapper);
    }

    @Scheduled(initialDelay = 15000L, fixedDelay = SYNC_INTERVAL_MILLIS)
    public void syncDueDataSources() {
        withRepositorySession(() -> {
            for (DataSourceDO dataSourceDO : getDataSourceMapper().selectList(new LambdaQueryWrapper<>())) {
                submitSync(dataSourceDO.getId(), false);
            }
            return null;
        });
    }

    private void submitSync(Long dataSourceId, boolean force) {
        if (dataSourceId == null) {
            return;
        }
        if (!force && !isDue(dataSourceId)) {
            return;
        }
        if (!runningDataSourceIds.add(dataSourceId)) {
            return;
        }
        executorService.submit(() -> {
            try {
                withRepositorySession(() -> {
                    syncOne(dataSourceId);
                    return null;
                });
            } finally {
                runningDataSourceIds.remove(dataSourceId);
            }
        });
    }

    private <T> T withRepositorySession(ContextSupplier<T> supplier) {
        boolean created = false;
        if (!Dbutils.hasSession()) {
            Dbutils.setSession();
            created = true;
        }
        try {
            return supplier.get();
        } finally {
            if (created) {
                Dbutils.removeSession();
            }
        }
    }

    private boolean isDue(Long dataSourceId) {
        ObjectSearchSyncStatusDO statusDO = findStatus(dataSourceId);
        return statusDO == null || statusDO.getNextSyncTime() == null || !statusDO.getNextSyncTime().after(new Date());
    }

    private void syncOne(Long dataSourceId) {
        DataSource dataSource = loadDataSource(dataSourceId);
        if (dataSource == null) {
            removeDataSource(dataSourceId);
            return;
        }
        ObjectSearchSyncStatusDO currentStatus = markRunning(findStatus(dataSourceId), dataSourceId);
        long syncVersion = System.currentTimeMillis();
        try {
            if (!supportsObjectSearch(dataSource)) {
                persistSuccess(dataSourceId, syncVersion, List.of(), currentStatus);
                return;
            }
            List<ObjectSearchIndexDO> items = collectIndexItems(dataSource, syncVersion);
            persistSuccess(dataSourceId, syncVersion, items, currentStatus);
        } catch (Exception exception) {
            log.warn("Object search sync failed for datasource {}", dataSourceId, exception);
            persistFailure(dataSourceId, currentStatus, exception);
        }
    }

    private boolean supportsObjectSearch(DataSource dataSource) {
        String dataSourceType = StringUtils.upperCase(StringUtils.trimToEmpty(dataSource.getType()), Locale.ROOT);
        return !UNSUPPORTED_OBJECT_SEARCH_TYPES.contains(dataSourceType);
    }

    private DataSource loadDataSource(Long dataSourceId) {
        DataSourceDO dataSourceDO = getDataSourceMapper().selectById(dataSourceId);
        if (dataSourceDO == null) {
            return null;
        }
        DataSource dataSource = dataSourceConverter.do2dto(dataSourceDO);
        fillSupportFlags(dataSource);
        return dataSource;
    }

    private void fillSupportFlags(DataSource dataSource) {
        DBConfig config = Chat2DBContext.getDBConfig(dataSource.getType());
        if (config != null) {
            dataSource.setSupportDatabase(config.isSupportDatabase());
            dataSource.setSupportSchema(config.isSupportSchema());
        }
    }

    private ObjectSearchSyncStatusDO findStatus(Long dataSourceId) {
        LambdaQueryWrapper<ObjectSearchSyncStatusDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ObjectSearchSyncStatusDO::getDataSourceId, dataSourceId);
        return getStatusMapper().selectOne(queryWrapper);
    }

    private ObjectSearchSyncStatusDO markRunning(ObjectSearchSyncStatusDO currentStatus, Long dataSourceId) {
        Date now = DateUtil.date();
        if (currentStatus == null) {
            ObjectSearchSyncStatusDO statusDO = new ObjectSearchSyncStatusDO();
            statusDO.setDataSourceId(dataSourceId);
            statusDO.setGmtCreate(now);
            statusDO.setGmtModified(now);
            statusDO.setLastSyncStatus(STATUS_RUNNING);
            statusDO.setNextSyncTime(new Date(now.getTime() + SYNC_INTERVAL_MILLIS));
            getStatusMapper().insert(statusDO);
            return statusDO;
        }
        currentStatus.setGmtModified(now);
        currentStatus.setLastSyncStatus(STATUS_RUNNING);
        currentStatus.setNextSyncTime(new Date(now.getTime() + SYNC_INTERVAL_MILLIS));
        getStatusMapper().updateById(currentStatus);
        return currentStatus;
    }

    private List<ObjectSearchIndexDO> collectIndexItems(DataSource dataSource, long syncVersion) {
        List<String> databases = resolveDatabases(dataSource);
        LinkedHashMap<String, ObjectSearchIndexDO> results = new LinkedHashMap<>();
        for (String databaseName : databases) {
            for (String schemaName : resolveSchemas(dataSource, databaseName)) {
                for (ObjectSearchIndexDO item : collectScopeItems(dataSource, databaseName, schemaName, syncVersion)) {
                    results.put(buildDeduplicateKey(item), item);
                }
            }
        }
        return new ArrayList<>(results.values());
    }

    private List<String> resolveDatabases(DataSource dataSource) {
        if (!dataSource.isSupportDatabase()) {
            return Collections.singletonList(null);
        }
        try {
            List<Database> databases = runWithContext(dataSource, null, null, () ->
                databaseService.queryAll(DatabaseQueryAllParam.builder()
                    .dataSourceId(dataSource.getId())
                    .refresh(true)
                    .dbType(dataSource.getType())
                    .build())
                    .getData()
            );
            List<String> names = normalizeNames(databases == null ? List.of() : databases.stream().map(Database::getName).toList());
            return names.isEmpty() ? fallbackDatabases(dataSource) : names;
        } catch (Exception exception) {
            log.debug("Resolve databases failed for datasource {}", dataSource.getId(), exception);
            return fallbackDatabases(dataSource);
        }
    }

    private List<String> resolveSchemas(DataSource dataSource, String databaseName) {
        if (!dataSource.isSupportSchema()) {
            return Collections.singletonList(null);
        }
        try {
            List<Schema> schemas = runWithContext(dataSource, databaseName, null, () ->
                databaseService.querySchema(SchemaQueryParam.builder()
                    .dataSourceId(dataSource.getId())
                    .dataBaseName(databaseName)
                    .refresh(true)
                    .build())
                    .getData()
            );
            List<String> names = normalizeNames(schemas == null ? List.of() : schemas.stream().map(Schema::getName).toList());
            return names.isEmpty() ? Collections.singletonList(null) : names;
        } catch (Exception exception) {
            log.debug("Resolve schemas failed for datasource {}, database {}", dataSource.getId(), databaseName, exception);
            return Collections.singletonList(null);
        }
    }

    private List<String> fallbackDatabases(DataSource dataSource) {
        return runWithContext(dataSource, null, null, () -> {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            addIfPresent(names, getCurrentCatalog(Chat2DBContext.getConnection()));
            addIfPresent(names, getDatabaseFromUrl(dataSource.getUrl()));
            if (names.isEmpty()) {
                names.add(null);
            }
            return new ArrayList<>(names);
        });
    }

    private List<String> normalizeNames(List<String> names) {
        return names.stream()
            .map(StringUtils::trimToNull)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    private List<ObjectSearchIndexDO> collectScopeItems(
        DataSource dataSource,
        String databaseName,
        String schemaName,
        long syncVersion
    ) {
        return runWithContext(dataSource, databaseName, schemaName, () -> {
            List<ObjectSearchIndexDO> items = new ArrayList<>();
            List<SimpleTable> tables = tableService.queryTables(TablePageQueryParam.builder()
                    .dataSourceId(dataSource.getId())
                    .databaseName(databaseName)
                    .schemaName(schemaName)
                    .refresh(true)
                    .build())
                .getData();
            if (tables != null) {
                for (SimpleTable table : tables) {
                    items.add(buildItem(dataSource, databaseName, schemaName, TYPE_TABLE, table.getName(), table.getComment(), syncVersion));
                }
            }

            List<Table> views = viewService.views(databaseName, schemaName).getData();
            if (views != null) {
                for (Table view : views) {
                    items.add(buildItem(
                        dataSource,
                        StringUtils.defaultIfBlank(view.getDatabaseName(), databaseName),
                        StringUtils.defaultIfBlank(view.getSchemaName(), schemaName),
                        TYPE_VIEW,
                        view.getName(),
                        view.getComment(),
                        syncVersion
                    ));
                }
            }

            List<Function> functions = functionService.functions(databaseName, schemaName).getData();
            if (functions != null) {
                for (Function function : functions) {
                    items.add(buildItem(
                        dataSource,
                        StringUtils.defaultIfBlank(function.getDatabaseName(), databaseName),
                        StringUtils.defaultIfBlank(function.getSchemaName(), schemaName),
                        TYPE_FUNCTION,
                        function.getFunctionName(),
                        function.getRemarks(),
                        syncVersion
                    ));
                }
            }

            List<Procedure> procedures = procedureService.procedures(databaseName, schemaName).getData();
            if (procedures != null) {
                for (Procedure procedure : procedures) {
                    items.add(buildItem(
                        dataSource,
                        StringUtils.defaultIfBlank(procedure.getDatabaseName(), databaseName),
                        StringUtils.defaultIfBlank(procedure.getSchemaName(), schemaName),
                        TYPE_PROCEDURE,
                        procedure.getProcedureName(),
                        procedure.getRemarks(),
                        syncVersion
                    ));
                }
            }

            List<Trigger> triggers = triggerService.triggers(databaseName, schemaName).getData();
            if (triggers != null) {
                for (Trigger trigger : triggers) {
                    items.add(buildItem(
                        dataSource,
                        StringUtils.defaultIfBlank(trigger.getDatabaseName(), databaseName),
                        StringUtils.defaultIfBlank(trigger.getSchemaName(), schemaName),
                        TYPE_TRIGGER,
                        trigger.getTriggerName(),
                        null,
                        syncVersion
                    ));
                }
            }
            return items.stream()
                .filter(item -> StringUtils.isNotBlank(item.getObjectName()))
                .toList();
        });
    }

    private ObjectSearchIndexDO buildItem(
        DataSource dataSource,
        String databaseName,
        String schemaName,
        String objectType,
        String objectName,
        String comment,
        long syncVersion
    ) {
        ObjectSearchIndexDO item = new ObjectSearchIndexDO();
        item.setDataSourceId(dataSource.getId());
        item.setDataSourceNameSnapshot(limitLength(
            StringUtils.defaultIfBlank(dataSource.getAlias(), "DataSource " + dataSource.getId()),
            DATA_SOURCE_NAME_MAX_LENGTH
        ));
        item.setDatabaseType(limitLength(dataSource.getType(), DATABASE_TYPE_MAX_LENGTH));
        item.setDatabaseName(limitLength(databaseName, DATABASE_NAME_MAX_LENGTH));
        item.setSchemaName(limitLength(schemaName, SCHEMA_NAME_MAX_LENGTH));
        item.setObjectType(limitLength(objectType, OBJECT_TYPE_MAX_LENGTH));
        item.setObjectName(limitLength(objectName, OBJECT_NAME_MAX_LENGTH));
        item.setComment(limitLength(comment, COMMENT_MAX_LENGTH));
        item.setSyncVersion(syncVersion);
        item.setDeleted("N");
        return item;
    }

    private String buildDeduplicateKey(ObjectSearchIndexDO item) {
        return String.join("|",
            String.valueOf(item.getDataSourceId()),
            StringUtils.defaultString(item.getDatabaseName()),
            StringUtils.defaultString(item.getSchemaName()),
            StringUtils.defaultString(item.getObjectType()),
            StringUtils.defaultString(item.getObjectName()).toLowerCase(Locale.ROOT)
        );
    }

    private void persistSuccess(
        Long dataSourceId,
        long syncVersion,
        List<ObjectSearchIndexDO> items,
        ObjectSearchSyncStatusDO currentStatus
    ) {
        Date now = DateUtil.date();
        if (CollectionUtils.isNotEmpty(items)) {
            for (int start = 0; start < items.size(); start += INSERT_BATCH_SIZE) {
                int end = Math.min(start + INSERT_BATCH_SIZE, items.size());
                getIndexMapper().batchInsert(items.subList(start, end));
            }
        }

        ObjectSearchSyncStatusDO statusDO = currentStatus == null ? new ObjectSearchSyncStatusDO() : currentStatus;
        if (statusDO.getId() == null) {
            statusDO.setGmtCreate(now);
            statusDO.setDataSourceId(dataSourceId);
        }
        statusDO.setGmtModified(now);
        statusDO.setLastSyncStatus(STATUS_SUCCESS);
        statusDO.setLastSyncTime(now);
        statusDO.setLastSyncError(null);
        statusDO.setLastSyncVersion(syncVersion);
        statusDO.setNextSyncTime(new Date(now.getTime() + SYNC_INTERVAL_MILLIS));
        upsertStatus(statusDO);

        LambdaQueryWrapper<ObjectSearchIndexDO> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ObjectSearchIndexDO::getDataSourceId, dataSourceId)
            .ne(ObjectSearchIndexDO::getSyncVersion, syncVersion);
        getIndexMapper().delete(deleteWrapper);
    }

    private void persistFailure(Long dataSourceId, ObjectSearchSyncStatusDO currentStatus, Exception exception) {
        Date now = DateUtil.date();
        ObjectSearchSyncStatusDO statusDO = currentStatus == null ? new ObjectSearchSyncStatusDO() : currentStatus;
        if (statusDO.getId() == null) {
            statusDO.setGmtCreate(now);
            statusDO.setDataSourceId(dataSourceId);
        }
        statusDO.setGmtModified(now);
        statusDO.setLastSyncStatus(STATUS_FAILED);
        statusDO.setLastSyncError(limitLength(
            StringUtils.defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName()),
            LAST_SYNC_ERROR_MAX_LENGTH
        ));
        statusDO.setNextSyncTime(new Date(now.getTime() + SYNC_INTERVAL_MILLIS));
        upsertStatus(statusDO);
    }

    private void upsertStatus(ObjectSearchSyncStatusDO statusDO) {
        if (statusDO.getId() == null) {
            getStatusMapper().insert(statusDO);
        } else {
            LambdaQueryWrapper<ObjectSearchSyncStatusDO> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ObjectSearchSyncStatusDO::getId, statusDO.getId());
            ObjectSearchSyncStatusDO updateDO = new ObjectSearchSyncStatusDO();
            updateDO.setGmtModified(statusDO.getGmtModified());
            updateDO.setLastSyncStatus(statusDO.getLastSyncStatus());
            updateDO.setLastSyncTime(statusDO.getLastSyncTime());
            updateDO.setLastSyncError(statusDO.getLastSyncError());
            updateDO.setLastSyncVersion(statusDO.getLastSyncVersion());
            updateDO.setNextSyncTime(statusDO.getNextSyncTime());
            getStatusMapper().update(updateDO, queryWrapper);
        }
    }

    private String limitLength(String value, int maxLength) {
        return StringUtils.left(StringUtils.trimToNull(value), maxLength);
    }

    private String getCurrentCatalog(Connection connection) {
        if (connection == null) {
            return null;
        }
        try {
            return StringUtils.trimToNull(connection.getCatalog());
        } catch (SQLException exception) {
            log.debug("Get current catalog failed", exception);
            return null;
        }
    }

    private String getDatabaseFromUrl(String url) {
        String normalizedUrl = StringUtils.trimToNull(url);
        if (normalizedUrl == null) {
            return null;
        }
        int queryIndex = normalizedUrl.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? normalizedUrl.substring(0, queryIndex) : normalizedUrl;
        int slashIndex = withoutQuery.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex >= withoutQuery.length() - 1) {
            return null;
        }
        String candidate = withoutQuery.substring(slashIndex + 1);
        if (candidate.contains(":")) {
            return null;
        }
        return StringUtils.trimToNull(candidate);
    }

    private void addIfPresent(Set<String> names, String name) {
        String normalized = StringUtils.trimToNull(name);
        if (normalized != null) {
            names.add(normalized);
        }
    }

    private <T> T runWithContext(DataSource dataSource, String databaseName, String schemaName, ContextSupplier<T> supplier) {
        try {
            Chat2DBContext.putContext(buildConnectInfo(dataSource, databaseName, schemaName));
            return supplier.get();
        } finally {
            Chat2DBContext.removeContext();
        }
    }

    private ConnectInfo buildConnectInfo(DataSource dataSource, String databaseName, String schemaName) {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setAlias(dataSource.getAlias());
        connectInfo.setUser(dataSource.getUserName());
        connectInfo.setDataSourceId(dataSource.getId());
        connectInfo.setPassword(dataSource.getPassword());
        connectInfo.setDbType(dataSource.getType());
        connectInfo.setUrl(dataSource.getUrl());
        connectInfo.setDatabase(databaseName);
        connectInfo.setSchemaName(schemaName);
        connectInfo.setConsoleOwn(false);
        connectInfo.setDriver(dataSource.getDriver());
        connectInfo.setSsh(dataSource.getSsh());
        connectInfo.setSsl(dataSource.getSsl());
        connectInfo.setJdbc(dataSource.getJdbc());
        connectInfo.setExtendInfo(dataSource.getExtendInfo());
        connectInfo.setPort(StringUtils.isNotBlank(dataSource.getPort()) ? Integer.parseInt(dataSource.getPort()) : null);
        connectInfo.setHost(dataSource.getHost());
        connectInfo.setLoginUser("system");
        DriverConfig driverConfig = dataSource.getDriverConfig();
        if (driverConfig != null && driverConfig.notEmpty()) {
            connectInfo.setDriverConfig(driverConfig);
        }
        String redisUrl = RedisJdbcUrlUtils.maybeBuildUrl(
            dataSource.getType(),
            dataSource.getHost(),
            dataSource.getPort(),
            databaseName,
            dataSource.getUserName(),
            dataSource.getPassword()
        );
        if (StringUtils.isNotBlank(redisUrl)) {
            connectInfo.setUrl(redisUrl);
        }
        return connectInfo;
    }

    @FunctionalInterface
    private interface ContextSupplier<T> {
        T get();
    }
}

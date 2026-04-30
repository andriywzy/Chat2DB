package ai.chat2db.server.web.api.controller.ai.platform.sql;

import ai.chat2db.server.domain.api.param.TablePageQueryParam;
import ai.chat2db.server.domain.api.service.TableService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.web.api.aspect.ConnectionInfoHandler;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.spi.model.SimpleTable;
import ai.chat2db.spi.sql.Chat2DBContext;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SqlOnlyResultValidator {

    private final TableService tableService;
    private final ConnectionInfoHandler connectionInfoHandler;

    public SqlOnlyResultValidator(TableService tableService, ConnectionInfoHandler connectionInfoHandler) {
        this.tableService = tableService;
        this.connectionInfoHandler = connectionInfoHandler;
    }

    public SqlOnlyValidationResult validate(ChatQueryRequest queryRequest, String sql) {
        return validate(sql, preloadAvailableTables(queryRequest));
    }

    public SqlOnlyValidationResult validate(String sql, Set<String> availableTables) {
        try {
            if (StringUtils.isBlank(sql)) {
                return SqlOnlyValidationResult.builder().valid(true).build();
            }

            Set<String> referencedTables = extractReferencedTables(sql);
            if (CollectionUtils.isEmpty(referencedTables)) {
                return SqlOnlyValidationResult.builder().valid(true).build();
            }

            if (CollectionUtils.isEmpty(availableTables)) {
                return SqlOnlyValidationResult.builder().valid(true).build();
            }

            List<String> missingTables = referencedTables.stream()
                .map(this::normalizeTableName)
                .filter(StringUtils::isNotBlank)
                .filter(tableName -> !availableTables.contains(tableName))
                .distinct()
                .toList();

            if (CollectionUtils.isEmpty(missingTables)) {
                return SqlOnlyValidationResult.builder().valid(true).build();
            }

            return SqlOnlyValidationResult.builder()
                .valid(false)
                .message("The generated SQL references tables that do not exist in the current active datasource: "
                    + String.join(", ", missingTables)
                    + ". Regenerate SQL using only tables from the current datasource.")
                .build();
        } catch (Exception e) {
            log.warn("skip sql-only validation due to unexpected error: {}", e.getMessage());
            return SqlOnlyValidationResult.builder().valid(true).build();
        }
    }

    public Set<String> preloadAvailableTables(ChatQueryRequest queryRequest) {
        try {
            if (queryRequest == null || queryRequest.getDataSourceId() == null) {
                return Set.of();
            }
            return loadAvailableTables(queryRequest);
        } catch (Exception e) {
            log.warn("skip sql-only table preload due to context unavailable: {}", e.getMessage());
            return Set.of();
        }
    }

    private Set<String> loadAvailableTables(ChatQueryRequest queryRequest) {
        boolean sessionInitialized = false;
        try {
            if (!Dbutils.hasSession()) {
                Dbutils.setSession();
                sessionInitialized = true;
            }
            Chat2DBContext.putContext(
                connectionInfoHandler.toInfo(
                    queryRequest.getDataSourceId(),
                    queryRequest.getDatabaseName(),
                    null,
                    queryRequest.getSchemaName()
                )
            );
            List<SimpleTable> tables = tableService.queryTables(TablePageQueryParam.builder()
                    .dataSourceId(queryRequest.getDataSourceId())
                    .databaseName(queryRequest.getDatabaseName())
                    .schemaName(queryRequest.getSchemaName())
                    .refresh(false)
                    .build())
                .getData();
            if (CollectionUtils.isEmpty(tables)) {
                return Set.of();
            }
            return tables.stream()
                .map(SimpleTable::getName)
                .filter(StringUtils::isNotBlank)
                .map(this::normalizeTableName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        } finally {
            Chat2DBContext.removeContext();
            if (sessionInitialized) {
                Dbutils.removeSession();
            }
        }
    }

    private Set<String> extractReferencedTables(String sql) {
        LinkedHashSet<String> tables = new LinkedHashSet<>();
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (statement instanceof Select select) {
                collectFromSelectBody(select.getSelectBody(), tables);
            } else if (statement instanceof Insert insert) {
                addTable(insert.getTable(), tables);
                if (insert.getSelect() != null) {
                    collectFromSelectBody(insert.getSelect().getSelectBody(), tables);
                }
            } else if (statement instanceof Update update) {
                addTable(update.getTable(), tables);
                collectFromItem(update.getFromItem(), tables);
                if (CollectionUtils.isNotEmpty(update.getJoins())) {
                    for (Join join : update.getJoins()) {
                        collectFromItem(join.getRightItem(), tables);
                    }
                }
                if (update.getSelect() != null) {
                    collectFromSelectBody(update.getSelect().getSelectBody(), tables);
                }
            } else if (statement instanceof Delete delete) {
                addTable(delete.getTable(), tables);
            } else if (statement instanceof Merge merge) {
                addTable(merge.getTable(), tables);
                addTable(merge.getUsingTable(), tables);
            }
        } catch (Exception ignore) {
            return Set.of();
        }
        return tables;
    }

    private void collectFromSelectBody(SelectBody selectBody, Set<String> tables) {
        if (selectBody instanceof PlainSelect plainSelect) {
            collectFromItem(plainSelect.getFromItem(), tables);
            if (CollectionUtils.isNotEmpty(plainSelect.getJoins())) {
                for (Join join : plainSelect.getJoins()) {
                    collectFromItem(join.getRightItem(), tables);
                }
            }
            return;
        }
        if (selectBody instanceof SetOperationList setOperationList
            && CollectionUtils.isNotEmpty(setOperationList.getSelects())) {
            for (SelectBody child : setOperationList.getSelects()) {
                collectFromSelectBody(child, tables);
            }
            return;
        }
        if (selectBody instanceof WithItem withItem
            && withItem.getSubSelect() != null
            && withItem.getSubSelect().getSelectBody() != null) {
            collectFromSelectBody(withItem.getSubSelect().getSelectBody(), tables);
        }
    }

    private void collectFromItem(FromItem fromItem, Set<String> tables) {
        if (fromItem instanceof Table table) {
            addTable(table, tables);
            return;
        }
        if (fromItem instanceof SubSelect subSelect && subSelect.getSelectBody() != null) {
            collectFromSelectBody(subSelect.getSelectBody(), tables);
        }
    }

    private void addTable(Table table, Set<String> tables) {
        if (table == null || StringUtils.isBlank(table.getName())) {
            return;
        }
        tables.add(normalizeTableName(table.getFullyQualifiedName()));
    }

    private String normalizeTableName(String tableName) {
        if (StringUtils.isBlank(tableName)) {
            return StringUtils.EMPTY;
        }
        String normalized = tableName
            .replace("`", StringUtils.EMPTY)
            .replace("\"", StringUtils.EMPTY)
            .replace("[", StringUtils.EMPTY)
            .replace("]", StringUtils.EMPTY)
            .trim();
        int lastDotIndex = normalized.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < normalized.length() - 1) {
            normalized = normalized.substring(lastDotIndex + 1);
        }
        return normalized.toLowerCase();
    }
}

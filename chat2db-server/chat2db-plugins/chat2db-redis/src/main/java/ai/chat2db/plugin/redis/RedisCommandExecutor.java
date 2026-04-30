package ai.chat2db.plugin.redis;

import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import ai.chat2db.server.tools.common.util.I18nUtils;
import ai.chat2db.spi.enums.DataTypeEnum;
import ai.chat2db.spi.enums.SqlTypeEnum;
import ai.chat2db.spi.model.Command;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.Header;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.SQLExecutor;
import cn.hutool.core.collection.CollectionUtil;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Executes Redis commands through a Redis JDBC driver.
 *
 * We intentionally avoid SQL parsing/splitting (Druid/SQL) and treat input as Redis commands. Results are normalized
 * to include the Chat2DB row-number column so the frontend table component works consistently.
 */
@Slf4j
public class RedisCommandExecutor extends SQLExecutor {

    @Override
    public List<ExecuteResult> execute(Command command) {
        if (command == null || StringUtils.isBlank(command.getScript())) {
            return Collections.emptyList();
        }

        List<String> scripts = splitScripts(command.getScript(), command.isSingle());
        if (CollectionUtils.isEmpty(scripts)) {
            throw new BusinessException("dataSource.sqlAnalysisError");
        }

        int pageNo = Optional.ofNullable(command.getPageNo()).orElse(1);
        int pageSize = Optional.ofNullable(command.getPageSize()).orElse(100);
        Integer offset = (pageNo - 1) * pageSize;
        Integer count = pageSize;

        Connection connection = Chat2DBContext.getConnection();
        List<ExecuteResult> results = new ArrayList<>(scripts.size());
        for (String script : scripts) {
            ExecuteResult result;
            try {
                // Redis commands are not SQL; just execute as-is through the JDBC driver.
                result = execute(script, connection, true, offset, count);
            } catch (SQLException e) {
                log.error("Execute redis command: {} exception", script, e);
                result = ExecuteResult.builder()
                        .sql(script)
                        .success(Boolean.FALSE)
                        .message(e.getMessage())
                        .build();
            }

            result.setOriginalSql(script);
            // Redis "commands" are closest to SELECT in Chat2DB UI terms (table-like result).
            result.setSqlType(SqlTypeEnum.SELECT.getCode());

            normalizeLists(result);
            addRowNumber(result, pageNo, pageSize);
            setPageInfo(result, pageNo, pageSize);

            results.add(result);
        }
        return results;
    }

    @Override
    public List<ExecuteResult> executeSelectTable(Command command) {
        return execute(command);
    }

    private static List<String> splitScripts(String script, boolean single) {
        if (single) {
            return Lists.newArrayList(script.trim());
        }
        // Split by semicolon or newline, trim blanks.
        List<String> parts = Arrays.asList(script.split("[;\\r\\n]+"));
        List<String> out = Lists.newArrayList();
        for (String p : parts) {
            if (StringUtils.isNotBlank(p)) {
                out.add(p.trim());
            }
        }
        return out;
    }

    private static void normalizeLists(ExecuteResult executeResult) {
        if (executeResult.getHeaderList() == null) {
            executeResult.setHeaderList(Lists.newArrayList());
        }
        if (executeResult.getDataList() == null) {
            executeResult.setDataList(Lists.newArrayList());
        }
    }

    private static void addRowNumber(ExecuteResult executeResult, int pageNo, int pageSize) {
        List<Header> headers = executeResult.getHeaderList();
        Header rowNumberHeader = Header.builder()
                .name(I18nUtils.getMessage("sqlResult.rowNumber"))
                .dataType(DataTypeEnum.CHAT2DB_ROW_NUMBER.getCode())
                .build();
        executeResult.setHeaderList(EasyCollectionUtils.union(Lists.newArrayList(rowNumberHeader), headers));

        if (CollectionUtil.isNotEmpty(executeResult.getDataList())) {
            int rowNumberIncrement = 1 + Math.max(pageNo - 1, 0) * pageSize;
            for (int i = 0; i < executeResult.getDataList().size(); i++) {
                List<String> row = executeResult.getDataList().get(i);
                List<String> newRow = Lists.newArrayListWithExpectedSize(row.size() + 1);
                newRow.add(Integer.toString(i + rowNumberIncrement));
                newRow.addAll(row);
                executeResult.getDataList().set(i, newRow);
            }
        }
    }

    private static void setPageInfo(ExecuteResult executeResult, int pageNo, int pageSize) {
        executeResult.setPageNo(pageNo);
        executeResult.setPageSize(pageSize);
        executeResult.setHasNextPage(CollectionUtils.size(executeResult.getDataList()) >= pageSize);

        int dataSize = CollectionUtils.size(executeResult.getDataList());
        int fuzzyTotal = Math.max(pageNo - 1, 0) * pageSize + dataSize;
        if (dataSize < pageSize) {
            executeResult.setFuzzyTotal(Integer.toString(fuzzyTotal));
        } else {
            executeResult.setFuzzyTotal(fuzzyTotal + "+");
        }
    }
}


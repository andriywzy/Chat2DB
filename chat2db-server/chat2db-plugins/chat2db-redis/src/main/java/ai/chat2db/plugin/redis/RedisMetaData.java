package ai.chat2db.plugin.redis;

import ai.chat2db.spi.CommandExecutor;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.jdbc.DefaultMetaService;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.sql.SQLExecutor;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RedisMetaData extends DefaultMetaService implements MetaData {

    private static final int DEFAULT_DATABASE_COUNT = 16;

    /**
     * Redis commonly exposes logical DB indexes (default 0..15). Query Redis first so an offline instance does not
     * appear as a healthy 16-database connection in the UI.
     */
    @Override
    public List<Database> databases(Connection connection) {
        int databaseCount = getDatabaseCount(connection);
        List<Database> databases = new ArrayList<>(databaseCount);
        for (int i = 0; i < databaseCount; i++) {
            databases.add(Database.builder().name(Integer.toString(i)).build());
        }
        return databases;
    }

    /**
     * Redis has logical DB indexes but no JDBC schema concept.
     * Returning empty list avoids calling JDBC metadata#getSchemas(), which is unsupported by the Redis driver.
     */
    @Override
    public List<Schema> schemas(Connection connection, String databaseName) {
        return new ArrayList<>();
    }

    @Override
    public List<Table> tables(Connection connection, String databaseName, String schemaName, String tableName) {
        String redisPattern = normalizeRedisPattern(tableName);
        String sql = "keys " + redisPattern;
        Set<String> names = SQLExecutor.getInstance().execute(connection, sql, resultSet -> {
            Set<String> keyNames = new LinkedHashSet<>();
            ResultSetMetaData metaData = resultSet.getMetaData();
            int colCount = metaData.getColumnCount();
            while (resultSet.next()) {
                for (int i = 1; i <= colCount; i++) {
                    String value = resultSet.getString(i);
                    if (isBlank(value)) {
                        continue;
                    }
                    collectKeyName(keyNames, value);
                }
            }
            return keyNames;
        });

        return names.stream().map(name -> Table.builder()
                .name(name)
                .databaseName(databaseName)
                .schemaName(schemaName)
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<String> tableNames(Connection connection, String databaseName, String schemaName, String tableName) {
        return tables(connection, databaseName, schemaName, tableName)
                .stream()
                .map(Table::getName)
                .collect(Collectors.toList());
    }

    @Override
    public CommandExecutor getCommandExecutor() {
        return new RedisCommandExecutor();
    }

    private static String normalizeRedisPattern(String tableName) {
        if (isBlank(tableName)) {
            return "*";
        }
        String pattern = tableName.trim().replace('%', '*');
        if (pattern.indexOf('*') < 0 && pattern.indexOf('?') < 0) {
            pattern = "*" + pattern + "*";
        }
        return pattern;
    }

    private static void collectKeyName(Set<String> keyNames, String rawValue) {
        String value = rawValue.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            String content = value.substring(1, value.length() - 1).trim();
            if (isBlank(content)) {
                return;
            }
            for (String item : content.split(",")) {
                String key = item.trim();
                if (!isBlank(key)) {
                    keyNames.add(key);
                }
            }
            return;
        }
        keyNames.add(value);
    }

    private static int getDatabaseCount(Connection connection) {
        try {
            Integer configuredCount = findPositiveInteger(executeCommandValues(connection, "config get databases"));
            if (configuredCount != null) {
                return configuredCount;
            }
        } catch (RuntimeException e) {
            // CONFIG can be disabled by ACL/policy. In that case still verify the connection before using Redis default.
            executeCommandValues(connection, "ping");
            return DEFAULT_DATABASE_COUNT;
        }
        executeCommandValues(connection, "ping");
        return DEFAULT_DATABASE_COUNT;
    }

    private static List<String> executeCommandValues(Connection connection, String command) {
        return SQLExecutor.getInstance().execute(connection, command, resultSet -> {
            List<String> values = new ArrayList<>();
            ResultSetMetaData metaData = resultSet.getMetaData();
            int colCount = metaData.getColumnCount();
            while (resultSet.next()) {
                for (int i = 1; i <= colCount; i++) {
                    String value = resultSet.getString(i);
                    if (!isBlank(value)) {
                        values.add(value.trim());
                    }
                }
            }
            return values;
        });
    }

    private static Integer findPositiveInteger(List<String> values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            try {
                int count = Integer.parseInt(value);
                if (count > 0) {
                    return count;
                }
            } catch (NumberFormatException ignored) {
                // Ignore Redis response labels like "databases" and keep scanning for the numeric value.
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

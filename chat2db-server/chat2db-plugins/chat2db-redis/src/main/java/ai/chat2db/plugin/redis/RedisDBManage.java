package ai.chat2db.plugin.redis;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.sql.SQLExecutor;

import java.sql.Connection;

/**
 * Redis is accessed via a Redis JDBC driver in this project. We only implement a minimal set of operations
 * needed by Chat2DB flows (connect/select DB, drop key).
 */
public class RedisDBManage extends DefaultDBManage implements DBManage {

    @Override
    public void connectDatabase(Connection connection, String database) {
        // Redis DB is selected through JDBC URL (/dbIndex). Avoid issuing SELECT here because
        // the third-party Redis JDBC driver may shift db index unexpectedly in some versions.
    }

    @Override
    public void dropTable(Connection connection, String databaseName, String schemaName, String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            return;
        }
        String sql = "del " + tableName;
        SQLExecutor.getInstance().execute(connection, sql, resultSet -> null);
    }
}

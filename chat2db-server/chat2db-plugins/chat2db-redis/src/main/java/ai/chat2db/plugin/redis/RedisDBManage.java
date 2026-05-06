package ai.chat2db.plugin.redis;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.jdbc.DefaultDBManage;
import ai.chat2db.spi.sql.ConnectInfo;
import ai.chat2db.spi.sql.SQLExecutor;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Redis is accessed via a Redis JDBC driver in this project. We only implement a minimal set of operations
 * needed by Chat2DB flows (connect/select DB, drop key).
 */
@Slf4j
public class RedisDBManage extends DefaultDBManage implements DBManage {

    @Override
    public Connection getConnection(ConnectInfo connectInfo) {
        Connection connection = super.getConnection(connectInfo);
        try {
            verifyConnection(connection);
            return connection;
        } catch (RuntimeException e) {
            if (!shouldRetryWithoutUsername(connectInfo, e)) {
                throw e;
            }

            log.info("Retry redis connection without username for datasourceId={}", connectInfo.getDataSourceId());
            closeFailedConnection(connection, connectInfo);
            connectInfo.setConnection(null);
            connectInfo.setSession(null);
            connectInfo.setUser(null);

            Connection retriedConnection = super.getConnection(connectInfo);
            verifyConnection(retriedConnection);
            return retriedConnection;
        }
    }

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

    private static void verifyConnection(Connection connection) {
        SQLExecutor.getInstance().execute(connection, "ping", resultSet -> null);
    }

    private static boolean shouldRetryWithoutUsername(ConnectInfo connectInfo, RuntimeException exception) {
        if (!StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(connectInfo.getUser()), "root")) {
            return false;
        }
        String message = getExceptionMessage(exception).toLowerCase();
        return message.contains("noauth") || message.contains("wrongpass") || message.contains("authentication required");
    }

    private static String getExceptionMessage(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.isNotBlank(current.getMessage())) {
                builder.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private static void closeFailedConnection(Connection connection, ConnectInfo connectInfo) {
        closeQuietly(connection);
        Session session = connectInfo.getSession();
        if (session != null) {
            try {
                if (connectInfo.getSsh() != null && connectInfo.getSsh().isUse()) {
                    session.delPortForwardingL(Integer.parseInt(connectInfo.getSsh().getLocalPort()));
                }
            } catch (Exception ignored) {
            }
            try {
                session.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }
}

package ai.chat2db.spi.util;

import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class RedisJdbcUrlUtils {

    private static final String REDIS_JDBC_PREFIX = "jdbc:redis://";

    private RedisJdbcUrlUtils() {
    }

    public static String buildUrl(String host, String port, String database, String username, String password) {
        if (StringUtils.isBlank(host) || StringUtils.isBlank(port)) {
            return null;
        }
        String dbName = StringUtils.isNotBlank(database) ? database.trim() : "0";
        StringBuilder builder = new StringBuilder(REDIS_JDBC_PREFIX);
        if (StringUtils.isNotBlank(username) || StringUtils.isNotBlank(password)) {
            if (StringUtils.isNotBlank(username)) {
                builder.append(encodeUserInfo(username.trim()));
                if (password != null) {
                    builder.append(':').append(encodeUserInfo(password));
                }
            } else {
                builder.append(encodeUserInfo(password));
            }
            builder.append('@');
        }
        builder.append(host.trim()).append(':').append(port.trim()).append('/').append(dbName);
        return builder.toString();
    }

    public static String maybeBuildUrl(String dbType, String host, String port, String database, String username, String password) {
        if (!StringUtils.equalsIgnoreCase(dbType, "REDIS")) {
            return null;
        }
        return buildUrl(host, port, database, username, password);
    }

    private static String encodeUserInfo(String value) {
        return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}

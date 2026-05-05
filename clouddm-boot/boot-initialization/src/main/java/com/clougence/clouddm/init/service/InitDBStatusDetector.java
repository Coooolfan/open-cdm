package com.clougence.clouddm.init.service;

import java.sql.*;
import java.util.Map;
import java.util.Properties;

import com.clougence.clouddm.console.web.constants.SystemStatus;
import com.clougence.clouddm.init.model.SystemStatusResult;
import com.clougence.utils.ResourcesUtils;
import com.clougence.utils.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class InitDBStatusDetector {

    private static final String TABLE_COUNT_SQL = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?";

    private InitDBStatusDetector(){
    }

    public static SystemStatusResult detectDBStatus(String... resourcePaths) {
        Properties props = new Properties();
        try {
            for (String resourcePath : resourcePaths) {
                if (StringUtils.isBlank(resourcePath)) {
                    continue;
                }
                Map<String, String> map = ResourcesUtils.getProperty(resourcePath);
                if (map != null) {
                    props.putAll(map);
                }
            }
        } catch (Exception e) {
            log.error("[InitDBStatusDetector] Failed to load DB config from resources", e);
            SystemStatusResult result = new SystemStatusResult();
            result.setStatus(SystemStatus.Initial);
            result.setInitReason("dbConfigMissing");
            result.setDbError(e.getMessage());
            return result;
        }
        return detectDBStatus(props);
    }

    public static SystemStatusResult detectDBStatus(Properties props) {
        SystemStatusResult result = new SystemStatusResult();
        String jdbcUrl = props == null ? null : props.getProperty("spring.datasource.jdbcurl");
        String username = props == null ? null : props.getProperty("spring.datasource.username");
        String password = props == null ? null : props.getProperty("spring.datasource.password");

        if (StringUtils.isBlank(jdbcUrl) || StringUtils.isBlank(username)) {
            result.setStatus(SystemStatus.Initial);
            result.setInitReason("dbConfigMissing");
            return result;
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            String dbName = getDatabaseName(jdbcUrl);
            log.info("[InitDBStatusDetector] Connected to DB: {}, dbName={}", jdbcUrl, dbName);
            try (PreparedStatement stmt = conn.prepareStatement(TABLE_COUNT_SQL)) {
                stmt.setString(1, dbName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        result.setStatus(SystemStatus.Initial);
                        result.setInitReason("dbEmpty");
                        return result;
                    }
                }
            }
            result.setStatus(SystemStatus.Ready);
            return result;
        } catch (SQLException e) {
            log.warn("[InitDBStatusDetector] DB connection failed: {}", e.getMessage());
            result.setStatus(SystemStatus.Initial);
            result.setInitReason("dbConnectionError");
            result.setDbError(e.getMessage());
            return result;
        }
    }

    public static String getDatabaseName(String jdbcUrl) {
        if (StringUtils.isBlank(jdbcUrl)) {
            return "";
        }
        int qIdx = jdbcUrl.indexOf('?');
        String urlWithoutQuery = qIdx > 0 ? jdbcUrl.substring(0, qIdx) : jdbcUrl;
        int slashIdx = urlWithoutQuery.lastIndexOf('/');
        if (slashIdx < 0) {
            return "";
        }
        return urlWithoutQuery.substring(slashIdx + 1);
    }
}

/*
 * Copyright 2026 杭州开云集致科技有限公司
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.clougence.clouddm.init.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import com.clougence.clouddm.console.web.constants.SystemStatus;
import com.clougence.clouddm.init.component.flyway.DmFlywayInit;
import com.clougence.clouddm.init.model.SystemStatusResult;
import com.clougence.clouddm.platform.dal.config.DmDalConfig;
import com.clougence.clouddm.platform.dal.config.MetaDbType;
import com.clougence.utils.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class InitDBStatusDetector {

    public static final String  PG_DEFAULT_SCHEMA = "public";
    private static final String TABLE_COUNT_SQL   = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE'";
    private static final String TABLE_EXISTS_SQL  = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?";
    private static final String DRIVER_MISSING    = "driverMissing";

    private InitDBStatusDetector(){
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

        MetaDbType dbType = MetaDbType.tryFromJdbcUrl(jdbcUrl);
        if (dbType == null) {
            result.setStatus(SystemStatus.Initial);
            result.setInitReason("dbConnectionError");
            result.setDbError("Unsupported metadata database jdbcUrl: " + jdbcUrl);
            return result;
        }

        try {
            DmDalConfig.ensureDriverAvailable(dbType);
        } catch (RuntimeException e) {
            log.warn("[InitDBStatusDetector] Runtime driver is not ready: {}", e.getMessage());
            result.setStatus(SystemStatus.Initial);
            result.setInitReason(DRIVER_MISSING);
            result.setDbError(e.getMessage());
            return result;
        }

        try (Connection conn = DmDalConfig.createDriverConnection(jdbcUrl, username, password, 10000L)) {
            String targetSchema = resolveTargetSchema(dbType, jdbcUrl);
            log.info("[InitDBStatusDetector] Connected to DB: {}, dbType={}, targetSchema={}", jdbcUrl, dbType, targetSchema);
            try (PreparedStatement stmt = conn.prepareStatement(TABLE_COUNT_SQL)) {
                stmt.setString(1, targetSchema);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        result.setStatus(SystemStatus.Initial);
                        result.setInitReason("dbEmpty");
                        return result;
                    }
                }
            }

            if (hasUpdateHistoryTable(conn, targetSchema)) {
                try {
                    List<String> pendingScripts = DmFlywayInit.listUpgradeRequiredScriptNames(jdbcUrl, username, password, targetSchema);
                    if (!pendingScripts.isEmpty()) {
                        result.setStatus(SystemStatus.Upgrade);
                        result.setInitReason("upgradePending");
                        result.setUpgradeScripts(pendingScripts);
                        return result;
                    }
                } catch (Exception e) {
                    log.warn("[InitDBStatusDetector] Flyway upgrade preview failed: {}", e.getMessage());
                    result.setStatus(SystemStatus.Upgrade);
                    result.setInitReason("upgradePending");
                    result.setDbError(e.getMessage());
                    return result;
                }
            }

            result.setStatus(SystemStatus.Ready);
            return result;
        } catch (SQLException e) {
            log.warn("[InitDBStatusDetector] DB connection failed: {}", e.getMessage());
            result.setStatus(SystemStatus.Initial);
            result.setInitReason(resolveConnectionErrorReason(e));
            result.setDbError(e.getMessage());
            return result;
        }
    }

    public static String resolveTargetSchema(MetaDbType dbType, String jdbcUrl) {
        if (dbType != null && dbType.isPostgresql()) {
            return PG_DEFAULT_SCHEMA;
        }
        return getDatabaseName(jdbcUrl);
    }

    static String resolveConnectionErrorReason(SQLException e) {
        if (isDriverMissing(e)) {
            return DRIVER_MISSING;
        }
        return isDatabaseMissing(e) ? "dbMissing" : "dbConnectionError";
    }

    private static boolean isDriverMissing(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof ClassNotFoundException || current instanceof NoClassDefFoundError) {
                return true;
            }

            String message = current.getMessage();
            if (StringUtils.isNotBlank(message)) {
                String lowerMessage = message.toLowerCase();
                if (lowerMessage.contains("classnotfoundexception")
                    || lowerMessage.contains("no suitable driver")
                    || lowerMessage.contains("runtime mysql driver")
                    || lowerMessage.contains("runtime postgresql driver")
                    || lowerMessage.contains("driver binding is unavailable")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    static boolean isDatabaseMissing(SQLException e) {
        if (e == null) {
            return false;
        }

        if (e.getErrorCode() == 1049 || "3D000".equals(e.getSQLState())) {
            return true;
        }

        String message = e.getMessage();
        if (StringUtils.isBlank(message)) {
            return false;
        }
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("unknown database") || (lowerMessage.contains("database") && lowerMessage.contains("does not exist"));
    }

    private static boolean hasUpdateHistoryTable(Connection conn, String targetSchema) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(TABLE_EXISTS_SQL)) {
            stmt.setString(1, targetSchema);
            stmt.setString(2, DmFlywayInit.TABLE);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
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

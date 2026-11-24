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
package com.clougence.clouddm.init.component.flyway;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.clougence.clouddm.init.component.log.InstallUpgradeLogBus;
import com.clougence.clouddm.platform.dal.config.DmDalConfig;
import com.clougence.clouddm.platform.dal.config.MetaDbType;
import com.clougence.utils.ExceptionUtils;
import com.clougence.utils.StringUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Configuration
public class DmFlywayInit {

    private static final boolean  BASELINE_ON_MIGRATE     = true;
    public static final String    SQL_MIGRATION_PREFIX    = "V";
    public static final String    SQL_MIGRATION_SEPARATOR = "__";
    private static final String[] SQL_MIGRATION_SUFFIXES  = { ".java" };
    private static final String   BASELINE_DESCRIPTION    = "<< CloudDM Init >>";
    public static final String[]  LOCATIONS               = { "classpath:com/clougence/clouddm/init/component/scripts" };
    public static final String[]  PG_LOCATIONS            = { "classpath:com/clougence/clouddm/init/component/scripts_pg" };
    private static final String   MYSQL_SCRIPTS_PATTERN   = "classpath*:com/clougence/clouddm/init/component/scripts/V*.class";
    private static final String   PG_SCRIPTS_PATTERN      = "classpath*:com/clougence/clouddm/init/component/scripts_pg/V*.class";
    public static final String    TABLE                   = "dm_update_history";

    @Resource
    private DataSource            dataSource;

    public void doUpgrade() {
        MetaDbType dbType = resolveDataSourceDbType();
        String currentSchema = fetchCurrentSchema(dbType);
        if (StringUtils.isBlank(currentSchema)) {
            throw new RuntimeException("Fetch currentSchema is empty.");
        }

        log.info("Start merged Flyway DB Upgrade, dbType={}, schema={}.", dbType, currentSchema);
        List<String> upgradeRequiredScripts = listUpgradeRequiredScriptNames(dataSource, dbType, currentSchema);
        InstallUpgradeLogBus.syncPlannedScripts(upgradeRequiredScripts);
        InstallUpgradeLogBus.info("Start Flyway migration, dbType=" + dbType.getDisplayName() + ", schema=" + currentSchema);
        if (!upgradeRequiredScripts.isEmpty()) {
            InstallUpgradeLogBus.info("Pending migration script count=" + upgradeRequiredScripts.size());
        }

        Flyway flyway = buildFlyway(new InstallUpgradeSqlLoggingDataSource(dataSource), dbType, currentSchema, new InstallUpgradeMigrationCallback());
        try {
            flyway.migrate();
        } catch (Exception e) {
            String msg = "[DmFlywayInit] merged Flyway DB Upgrade failed, msg : " + ExceptionUtils.getRootCauseMessage(e);
            log.error(msg);
            InstallUpgradeLogBus.markCurrentScriptFailed(e);
            InstallUpgradeLogBus.error(msg, e);
            throw e;
        }
        InstallUpgradeLogBus.info("Flyway migration finished.");
        log.info("Merged Flyway DB Upgrade Done.");
    }

    public List<String> listUpgradeRequiredScriptNames() {
        MetaDbType dbType = resolveDataSourceDbType();
        String currentSchema = fetchCurrentSchema(dbType);
        if (StringUtils.isBlank(currentSchema)) {
            throw new RuntimeException("Fetch currentSchema is empty.");
        }
        return listUpgradeRequiredScriptNames(dataSource, dbType, currentSchema);
    }

    public void doUpgradeAndValidate() {
        doUpgrade();
        List<String> remainingScripts = listUpgradeRequiredScriptNames();
        if (!remainingScripts.isEmpty()) {
            throw new IllegalStateException("Upgrade verification failed. Pending scripts remain: " + String.join(", ", remainingScripts));
        }
    }

    public static List<String> listUpgradeRequiredScriptNames(String jdbcUrl, String username, String password, String currentSchema) {
        MetaDbType dbType = MetaDbType.fromJdbcUrl(jdbcUrl);
        return extractUpgradeRequiredScriptNames(buildFlyway(jdbcUrl, username, password, dbType, currentSchema).info().all());
    }

    public static List<String> listAllScriptNames() {
        return listAllScriptNames(MetaDbType.MYSQL);
    }

    public static List<String> listAllScriptNames(MetaDbType dbType) {
        MetaDbType effectiveType = dbType == null ? MetaDbType.MYSQL : dbType;
        String pattern = effectiveType.isPostgresql() ? PG_SCRIPTS_PATTERN : MYSQL_SCRIPTS_PATTERN;
        List<String> scriptNames = new ArrayList<>();
        try {
            org.springframework.core.io.Resource[] resources = new PathMatchingResourcePatternResolver().getResources(pattern);
            for (org.springframework.core.io.Resource resource : resources) {
                if (resource == null || resource.getFilename() == null) {
                    continue;
                }
                String filename = resource.getFilename();
                if (!filename.endsWith(".class") || filename.contains("$")) {
                    continue;
                }
                scriptNames.add(filename.substring(0, filename.length() - ".class".length()));
            }
        } catch (Exception e) {
            throw new RuntimeException("List Flyway script names failed: " + ExceptionUtils.getRootCauseMessage(e), e);
        }
        scriptNames.sort(String::compareTo);
        return scriptNames;
    }

    private static List<String> listUpgradeRequiredScriptNames(DataSource dataSource, MetaDbType dbType, String currentSchema) {
        return extractUpgradeRequiredScriptNames(buildFlyway(dataSource, dbType, currentSchema).info().all());
    }

    private static Flyway buildFlyway(DataSource dataSource, MetaDbType dbType, String currentSchema) {
        return buildFlyway(dataSource, dbType, currentSchema, null);
    }

    private static Flyway buildFlyway(DataSource dataSource, MetaDbType dbType, String currentSchema, Callback callback) {
        String[] locations = resolveLocations(dbType);
        return Flyway.configure()
            .dataSource(dataSource)
            .locations(locations)
            .createSchemas(false)
            .defaultSchema(currentSchema)
            .baselineOnMigrate(BASELINE_ON_MIGRATE)
            .baselineDescription(BASELINE_DESCRIPTION)
            .sqlMigrationPrefix(SQL_MIGRATION_PREFIX)
            .sqlMigrationSeparator(SQL_MIGRATION_SEPARATOR)
            .sqlMigrationSuffixes(SQL_MIGRATION_SUFFIXES)
            .table(TABLE)
            .outOfOrder(true)
            .callbacks(callback == null ? new Callback[0] : new Callback[] { callback })
            .load();
    }

    private static Flyway buildFlyway(String jdbcUrl, String username, String password, MetaDbType dbType, String currentSchema) {
        DataSource ds = DmDalConfig.createDriverDataSource(jdbcUrl, username, password, 10000L);
        return buildFlyway(ds, dbType, currentSchema);
    }

    private static String[] resolveLocations(MetaDbType dbType) {
        if (dbType != null && dbType.isPostgresql()) {
            return PG_LOCATIONS;
        }
        return LOCATIONS;
    }

    private static String extractScriptName(MigrationInfo migrationInfo) {
        if (migrationInfo == null) {
            return "";
        }
        String version = migrationInfo.getVersion() == null ? "" : migrationInfo.getVersion().getVersion();
        String description = StringUtils.defaultIfBlank(migrationInfo.getDescription(), migrationInfo.getScript());
        description = description.replace(' ', '_');
        if (StringUtils.isNotBlank(version)) {
            return SQL_MIGRATION_PREFIX + version + SQL_MIGRATION_SEPARATOR + description;
        }
        return description;
    }

    private static List<String> extractUpgradeRequiredScriptNames(MigrationInfo[] migrationInfos) {
        List<String> scriptNames = new ArrayList<>();
        if (migrationInfos == null) {
            return scriptNames;
        }

        for (MigrationInfo migrationInfo : migrationInfos) {
            if (!isUpgradeRequired(migrationInfo)) {
                continue;
            }

            String scriptName = extractScriptName(migrationInfo);
            if (StringUtils.isNotBlank(scriptName)) {
                scriptNames.add(scriptName);
            }
        }
        return scriptNames;
    }

    private static boolean isUpgradeRequired(MigrationInfo migrationInfo) {
        if (migrationInfo == null || migrationInfo.getState() == null) {
            return false;
        }

        String stateName = migrationInfo.getState().name();
        return "PENDING".equals(stateName) || stateName.contains("FAILED");
    }

    private static final class InstallUpgradeMigrationCallback implements Callback {

        @Override
        public boolean supports(Event event, Context context) {
            return event == Event.BEFORE_EACH_MIGRATE || event == Event.AFTER_EACH_MIGRATE;
        }

        @Override
        public boolean canHandleInTransaction(Event event, Context context) {
            return true;
        }

        @Override
        public void handle(Event event, Context context) {
            String scriptName = extractScriptName(context == null ? null : context.getMigrationInfo());
            if (StringUtils.isBlank(scriptName)) {
                return;
            }
            if (event == Event.BEFORE_EACH_MIGRATE) {
                InstallUpgradeLogBus.markScriptRunning(scriptName);
                return;
            }
            InstallUpgradeLogBus.markScriptSuccess(scriptName);
        }

        @Override
        public String getCallbackName() {
            return "clouddmInstallUpgradeMigrationCallback";
        }
    }

    private MetaDbType resolveDataSourceDbType() {
        try (Connection c = dataSource.getConnection()) {
            String productName = c.getMetaData().getDatabaseProductName();
            if (productName != null && productName.toLowerCase().contains("postgresql")) {
                return MetaDbType.POSTGRESQL;
            }
            return MetaDbType.MYSQL;
        } catch (SQLException e) {
            String msg = "Resolve database product type failed, msg:" + ExceptionUtils.getRootCauseMessage(e);
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    private String fetchCurrentSchema(MetaDbType dbType) {
        String sql = dbType.isPostgresql() ? "SELECT current_schema()" : "SELECT database()";
        try (Connection c = dataSource.getConnection()) {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            String msg = "Fix flyway migration info failed.msg:" + ExceptionUtils.getRootCauseMessage(e);
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }

        return null;
    }
}

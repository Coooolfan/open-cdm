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
package com.clougence.clouddm.init;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DuplicateKeyException;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.clougence.clouddm.console.web.constants.SystemStatus;
import com.clougence.clouddm.init.component.fixtasks.DmFixDefaultClusterWorker;
import com.clougence.clouddm.init.component.fixtasks.DmFixSecRules;
import com.clougence.clouddm.init.component.fixtasks.InitConsolePluginLoader;
import com.clougence.clouddm.init.component.fixtasks.RdpFixUserRole;
import com.clougence.clouddm.init.component.flyway.DmFlywayInit;
import com.clougence.clouddm.init.constant.InitSeedConstants;
import com.clougence.clouddm.init.model.SystemStatusResult;
import com.clougence.clouddm.init.model.TestDbResult;
import com.clougence.clouddm.init.service.InitDBStatusDetector;
import com.clougence.clouddm.init.service.SysInitService;
import com.clougence.clouddm.platform.dal.access.*;
import com.clougence.clouddm.platform.dal.config.DmDalConfig;
import com.clougence.clouddm.platform.dal.config.MetaDbType;
import com.clougence.clouddm.platform.dal.model.approval.DmApprovalProcessActivityDO;
import com.clougence.clouddm.platform.dal.model.auth.AccountBindType;
import com.clougence.clouddm.platform.dal.model.auth.AccountType;
import com.clougence.clouddm.platform.dal.model.auth.DmAuthResDO;
import com.clougence.clouddm.platform.dal.model.auth.DmAuthRoleDO;
import com.clougence.clouddm.platform.dal.model.auth.DmAuthSessionDO;
import com.clougence.clouddm.platform.dal.model.auth.DmAuthUserDO;
import com.clougence.clouddm.platform.dal.model.auth.UserStatus;
import com.clougence.clouddm.platform.dal.model.cicd.ChangeTransferStatus;
import com.clougence.clouddm.platform.dal.model.cicd.DmChangeTransferDO;
import com.clougence.clouddm.platform.dal.model.cicd.DmChangeTriggerReceiptDO;
import com.clougence.clouddm.base.metadata.ds.DataSourceType;
import com.clougence.clouddm.platform.dal.model.LifeCycleState;
import com.clougence.clouddm.platform.dal.model.datasource.DmDsDO;
import com.clougence.clouddm.platform.dal.model.datasource.DmDsMetaDataDO;
import com.clougence.clouddm.platform.dal.model.datasource.MetaInformationType;
import com.clougence.clouddm.platform.dal.model.execution.AsyncTaskProcessType;
import com.clougence.clouddm.platform.dal.model.execution.AsyncTaskStatus;
import com.clougence.clouddm.platform.dal.model.execution.DmExecAsyncTaskDO;
import com.clougence.clouddm.platform.dal.model.execution.DmExecSqlAuditDO;
import com.clougence.clouddm.platform.dal.model.execution.DmExecSqlScriptDO;
import com.clougence.clouddm.platform.dal.model.execution.SqlScriptStatus;
import com.clougence.clouddm.platform.dal.model.gitops.ScmType;
import com.clougence.clouddm.platform.dal.model.secrule.DmSecSpecDO;
import com.clougence.clouddm.platform.dal.model.system.DmSysAttachmentDO;
import com.clougence.clouddm.platform.dal.model.system.DmSysEnvDO;
import com.clougence.clouddm.platform.dal.model.system.SysAttachmentStatus;
import com.clougence.clouddm.platform.dal.model.system.SysAttachmentType;
import com.clougence.clouddm.platform.dal.util.PageObj;
import com.clougence.clouddm.platform.dal.util.PageUtils;
import com.clougence.clouddm.sdk.security.auth.AuthKind;
import com.clougence.clouddm.api.console.sqlaudit.SqlStatus;
import com.clougence.clouddm.sdk.service.secrules.Requester;

public class DualMetaDbIntegrationTest {

    @Test
    public void testDetectorAndScriptDiscoveryUnit() {
        Assert.assertEquals("cdmgr", InitDBStatusDetector.resolveTargetSchema(MetaDbType.MYSQL, "jdbc:mysql://127.0.0.1:3306/cdmgr"));
        Assert.assertEquals("public", InitDBStatusDetector.resolveTargetSchema(MetaDbType.POSTGRESQL, "jdbc:postgresql://127.0.0.1:5432/cdmgr"));

        List<String> mysqlScripts = DmFlywayInit.listAllScriptNames(MetaDbType.MYSQL);
        List<String> pgScripts = DmFlywayInit.listAllScriptNames(MetaDbType.POSTGRESQL);

        Assert.assertTrue("MySQL scripts should contain historical migrations", mysqlScripts.size() >= 70);
        Assert.assertFalse("MySQL scripts must not scan PG baseline", mysqlScripts.contains("V202611100001__pg_baseline_init"));
        Assert.assertTrue("PG scripts must contain PG baseline", pgScripts.contains("V202611100001__pg_baseline_init"));
        Assert.assertFalse("PG scripts must not scan MySQL scripts", pgScripts.contains("V202605070001__init_sql"));
    }

    @Test
    public void testPg17VersionRejected() {
        String pg17Url = resolveConfig("dm.test.pg17.url", "DM_TEST_PG17_URL");
        String pg17User = resolveConfig("dm.test.pg17.user", "DM_TEST_PG17_USER");
        String pg17Pass = resolveConfig("dm.test.pg17.password", "DM_TEST_PG17_PASSWORD");
        Assume.assumeTrue("Skip PG 17 version gate test when dm.test.pg17.url is not configured", isPresent(pg17Url));

        SysInitService initService = new SysInitService();
        TestDbResult result = initService.testDbConnection(pg17Url, pg17User, pg17Pass);
        Assert.assertFalse("PG 17 must be rejected", result.isSuccess());
        Assert.assertFalse(result.isCanProceed());
        Assert.assertEquals("error", result.getMessageType());
    }

    @Test
    public void testPg18AutoCreateBootstrapAndFixTasks() throws Exception {
        String pgUrl = resolveConfig("dm.test.pg.url", "DM_TEST_PG_URL");
        String pgUser = resolveConfig("dm.test.pg.user", "DM_TEST_PG_USER");
        String pgPass = resolveConfig("dm.test.pg.password", "DM_TEST_PG_PASSWORD");
        Assume.assumeTrue("Skip PG 18 integration test when dm.test.pg.url is not configured", isPresent(pgUrl));

        String autoCreateDb = "cdmgr_pg_auto_" + System.currentTimeMillis();
        String autoCreateUrl = replaceDatabaseName(pgUrl, autoCreateDb);
        String maintenanceUrl = replaceDatabaseName(pgUrl, "postgres");

        try {
            // 1. Missing DB should be detected as dbMissing
            Properties detectProps = buildDataSourceProps(autoCreateUrl, pgUser, pgPass);
            SystemStatusResult missingStatus = InitDBStatusDetector.detectDBStatus(detectProps);
            Assert.assertEquals(SystemStatus.Initial, missingStatus.getStatus());
            Assert.assertEquals("dbMissing", missingStatus.getInitReason());

            // 2. testDbConnection should report createDatabase=true and canProceed=true
            SysInitService initService = new SysInitService();
            TestDbResult testResult = initService.testDbConnection(autoCreateUrl, pgUser, pgPass);
            Assert.assertTrue("testDbConnection should succeed for missing DB: " + testResult.getMessage(), testResult.isSuccess());
            Assert.assertTrue(testResult.isCanProceed());
            Assert.assertTrue(testResult.isCreateDatabase());
            Assert.assertFalse(testResult.isDatabaseExists());

            // 3. prepareDatabase with createIfMissing=true creates UTF8 database
            initService.prepareDatabase(autoCreateUrl, pgUser, pgPass, true);

            SystemStatusResult emptyStatus = InitDBStatusDetector.detectDBStatus(detectProps);
            Assert.assertEquals(SystemStatus.Initial, emptyStatus.getStatus());
            Assert.assertEquals("dbEmpty", emptyStatus.getInitReason());

            // 4. Run Flyway migration + idempotent re-run + updateAdminUser + fix tasks
            try (ConfigurableApplicationContext ctx = startTaskContext(autoCreateUrl, pgUser, pgPass)) {
                DmFlywayInit flywayInit = ctx.getBean(DmFlywayInit.class);
                flywayInit.doUpgradeAndValidate();
                flywayInit.doUpgradeAndValidate();

                initService.updateAdminUser(autoCreateUrl, pgUser, pgPass, "admin", "admin@cdmgr.com", "Admin123456!");

                ctx.getBean(InitConsolePluginLoader.class).loadPlugin(InitTaskApplication.class.getClassLoader());
                ctx.getBean(RdpFixUserRole.class).init();
                ctx.getBean(DmFixSecRules.class).init();
                ctx.getBean(DmFixDefaultClusterWorker.class).init();
            }

            // 5. Detector should now report Ready
            SystemStatusResult readyStatus = InitDBStatusDetector.detectDBStatus(detectProps);
            Assert.assertEquals(SystemStatus.Ready, readyStatus.getStatus());

            // 6. Verify non-UTF8 PG database and lack of public schema CREATE privilege are rejected
            verifyPgCharsetAndPrivilegeGuards(maintenanceUrl, pgUrl, pgUser, pgPass);
        } finally {
            dropPgDatabaseQuietly(maintenanceUrl, pgUser, pgPass, autoCreateDb);
        }
    }

    private void verifyPgCharsetAndPrivilegeGuards(String maintenanceUrl, String basePgUrl, String adminUser, String adminPass) throws Exception {
        String ts = Long.toString(System.currentTimeMillis());
        String asciiDb = "cdmgr_pg_ascii_" + ts;
        String noPrivRole = "cgdm_nopriv_" + ts;
        String noPrivPass = "NoPriv123456!";
        String asciiUrl = replaceDatabaseName(basePgUrl, asciiDb);

        try (Connection conn = DmDalConfig.createDriverConnection(maintenanceUrl, adminUser, adminPass, 5000L);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE \"" + asciiDb + "\" WITH ENCODING 'SQL_ASCII' TEMPLATE template0");
            stmt.execute("CREATE ROLE \"" + noPrivRole + "\" LOGIN PASSWORD '" + noPrivPass + "'");
        }

        try {
            SysInitService initService = new SysInitService();

            TestDbResult asciiResult = initService.testDbConnection(asciiUrl, adminUser, adminPass);
            Assert.assertFalse("Non-UTF8 PG database must be rejected", asciiResult.isSuccess());
            Assert.assertFalse(asciiResult.isCharsetValid());

            TestDbResult noPrivResult = initService.testDbConnection(basePgUrl, noPrivRole, noPrivPass);
            Assert.assertFalse("PG user without public schema CREATE privilege must be rejected", noPrivResult.isSuccess());
            Assert.assertFalse(noPrivResult.isCanProceed());
        } finally {
            dropPgDatabaseQuietly(maintenanceUrl, adminUser, adminPass, asciiDb);
            try (Connection conn = DmDalConfig.createDriverConnection(maintenanceUrl, adminUser, adminPass, 5000L);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP ROLE IF EXISTS \"" + noPrivRole + "\"");
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    public void testDualSchemaParity() throws Exception {
        String mysqlUrl = resolveConfig("dm.test.mysql.url", "DM_TEST_MYSQL_URL");
        String mysqlUser = resolveConfig("dm.test.mysql.user", "DM_TEST_MYSQL_USER");
        String mysqlPass = resolveConfig("dm.test.mysql.password", "DM_TEST_MYSQL_PASSWORD");
        String pgUrl = resolveConfig("dm.test.pg.url", "DM_TEST_PG_URL");
        String pgUser = resolveConfig("dm.test.pg.user", "DM_TEST_PG_USER");
        String pgPass = resolveConfig("dm.test.pg.password", "DM_TEST_PG_PASSWORD");
        Assume.assumeTrue("Skip dual schema parity when mysql or pg test URL is missing", isPresent(mysqlUrl) && isPresent(pgUrl));

        runFlywayUpgrade(mysqlUrl, mysqlUser, mysqlPass);
        runFlywayUpgrade(pgUrl, pgUser, pgPass);

        Map<String, Set<String>> mysqlSchema = loadTableColumns(mysqlUrl, mysqlUser, mysqlPass, InitDBStatusDetector.getDatabaseName(mysqlUrl));
        Map<String, Set<String>> pgSchema = loadTableColumns(pgUrl, pgUser, pgPass, InitDBStatusDetector.PG_DEFAULT_SCHEMA);

        Assert.assertEquals("Table set mismatch between MySQL and PostgreSQL", mysqlSchema.keySet(), pgSchema.keySet());
        for (Map.Entry<String, Set<String>> entry : mysqlSchema.entrySet()) {
            String table = entry.getKey();
            Assert.assertEquals("Column set mismatch on table: " + table, entry.getValue(), pgSchema.get(table));
        }
    }

    @Test
    public void testDualMapperCompatibility() throws Exception {
        String mysqlUrl = resolveConfig("dm.test.mysql.url", "DM_TEST_MYSQL_URL");
        String mysqlUser = resolveConfig("dm.test.mysql.user", "DM_TEST_MYSQL_USER");
        String mysqlPass = resolveConfig("dm.test.mysql.password", "DM_TEST_MYSQL_PASSWORD");
        if (isPresent(mysqlUrl)) {
            runFlywayUpgrade(mysqlUrl, mysqlUser, mysqlPass);
            verifyMapperSuite(mysqlUrl, mysqlUser, mysqlPass, MetaDbType.MYSQL);
        }

        String pgUrl = resolveConfig("dm.test.pg.url", "DM_TEST_PG_URL");
        String pgUser = resolveConfig("dm.test.pg.user", "DM_TEST_PG_USER");
        String pgPass = resolveConfig("dm.test.pg.password", "DM_TEST_PG_PASSWORD");
        if (isPresent(pgUrl)) {
            runFlywayUpgrade(pgUrl, pgUser, pgPass);
            verifyMapperSuite(pgUrl, pgUser, pgPass, MetaDbType.POSTGRESQL);
        }

        Assume.assumeTrue("Skip mapper compatibility when neither MySQL nor PG test URL is configured", isPresent(mysqlUrl) || isPresent(pgUrl));
    }

    private void verifyMapperSuite(String jdbcUrl, String user, String pass, MetaDbType dbType) throws Exception {
        String suffix = Long.toString(System.nanoTime());
        try (ConfigurableApplicationContext ctx = startTaskContext(jdbcUrl, user, pass)) {
            AuthDal authDal = ctx.getBean(AuthDal.class);
            SystemDal systemDal = ctx.getBean(SystemDal.class);
            AiDal aiDal = ctx.getBean(AiDal.class);
            DataSourceDal dsDal = ctx.getBean(DataSourceDal.class);
            ChangeFlowDal changeFlowDal = ctx.getBean(ChangeFlowDal.class);
            ApprovalDal approvalDal = ctx.getBean(ApprovalDal.class);
            ExecutionDal executionDal = ctx.getBean(ExecutionDal.class);
            SecRuleDal secRuleDal = ctx.getBean(SecRuleDal.class);

            // 1. Seed table IDENTITY sequence continuity & IdType.AUTO key backfill
            DmSysEnvDO env = new DmSysEnvDO();
            env.setOwnerUid(InitSeedConstants.ADMIN_UID);
            env.setEnvName("Env_" + suffix);
            env.setDescription("desc");
            Assert.assertEquals(1, systemDal.envMapper().insert(env));
            Assert.assertNotNull(env.getId());
            Assert.assertTrue("Seed sequence must advance beyond id=1", env.getId() > 1L);

            DmAuthRoleDO role = new DmAuthRoleDO();
            role.setOwnerUid(InitSeedConstants.ADMIN_UID);
            role.setRoleName("Role_" + suffix);
            role.setAliasName("Role_" + suffix);
            role.setRoleAuthLabels(List.of("DM_AI_ENGINE_MANAGE", "CUSTOM_LABEL_" + suffix));
            role.setInnerTag(false);
            Assert.assertEquals(1, authDal.roleMapper().insert(role));
            Assert.assertNotNull(role.getId());
            Assert.assertTrue(role.getId() > 1L);

            // 2. JSON_CONTAINS / jsonb @> on dm_auth_role.role_auth_labels + sub-account insertion
            DmAuthUserDO subUser = new DmAuthUserDO();
            subUser.setUid("sub_" + suffix);
            subUser.setUsername("SubUser_" + suffix);
            subUser.setAccount("SubAcc_" + suffix);
            subUser.setEmail("Sub_" + suffix + "@Example.COM");
            subUser.setPassword("hashed_pwd");
            subUser.setRoleId(role.getId());
            subUser.setAccessKey("ak_" + suffix);
            subUser.setSecretKey("sk_" + suffix);
            subUser.setLastTryLoginTime(new Date());
            subUser.setLoginFailCount(0);
            subUser.setLoginLocked(false);
            subUser.setAccountType(AccountType.SUB_ACCOUNT);
            subUser.setDisable(false);
            subUser.setParentId(1L);
            subUser.setMaintainer(false);
            subUser.setBindType(AccountBindType.INTERNAL);
            subUser.setAllowLocal(true);
            subUser.setUserStatus(UserStatus.NORMAL);
            subUser.setUseMfa(false);
            subUser.setNeedChangePwd(false);
            Assert.assertEquals(1, authDal.userMapper().insert(subUser));
            Assert.assertNotNull(subUser.getId());
            Assert.assertTrue(subUser.getId() > 1L);

            List<DmAuthUserDO> byAuthLabel = authDal.userMapper().listSubAccountByPidAndAuth(1L, "CUSTOM_LABEL_" + suffix);
            Assert.assertEquals(1, byAuthLabel.size());
            Assert.assertEquals(subUser.getUid(), byAuthLabel.get(0).getUid());

            // 3. Case-insensitive equality (=), LIKE, and UNIQUE constraint parity
            List<DmAuthUserDO> byLowerUsername = authDal.userMapper().listByUserName(("subuser_" + suffix).toLowerCase(Locale.ROOT));
            Assert.assertEquals("Username equality must be case-insensitive on " + dbType, 1, byLowerUsername.size());

            // 4. Reverse LIKE (#{item} LIKE res_path || '%') case-insensitive match
            long uniqueResId = 90000L + (System.nanoTime() % 1000000L);
            DmAuthResDO authRes = new DmAuthResDO();
            authRes.setOwnerUid(subUser.getUid());
            authRes.setResId(uniqueResId);
            authRes.setResInstId("inst_" + suffix);
            authRes.setResDesc("Test resource");
            authRes.setLevelOne("CatalogA");
            authRes.setKindType(AuthKind.DataSource);
            authRes.setResPath("/CatalogA/SchemaB");
            authRes.setAuthLabels(List.of("QUERY"));
            Assert.assertEquals(1, authDal.resMapper().insert(authRes));
            List<DmAuthResDO> matchedRes = authDal.resMapper().queryByPathLike(uniqueResId, subUser.getUid(), AuthKind.DataSource, List.of("/cataloga/schemab/table1"));
            Assert.assertEquals("Reverse LIKE must match case-insensitively on " + dbType, 1, matchedRes.size());
            Assert.assertEquals("queryApproPerson reverse LIKE must match case-insensitively on " + dbType, 1,
                    authDal.userMapper().queryApproPerson(AccountType.SUB_ACCOUNT, 1L, uniqueResId, "/cataloga/schemab/table1").size());

            // 5. Binary (_bin) column remains case-sensitive (dm_auth_session.token_hash) + batch DELETE LIMIT
            String tokenHashUpper = ("A" + suffix + "0000000000000000000000000000000000000000000000000000000000000000").substring(0, 64);
            String tokenHashLower = tokenHashUpper.toLowerCase(Locale.ROOT);
            DmAuthSessionDO session = new DmAuthSessionDO();
            session.setUid(subUser.getUid());
            session.setTokenHash(tokenHashUpper);
            session.setLoginType("LOCAL");
            session.setGmtCreate(System.currentTimeMillis());
            session.setExpireTimeMs(1000L);
            Assert.assertEquals(1, authDal.sessionMapper().insert(session));
            Assert.assertNotNull(authDal.sessionMapper().queryByTokenHash(tokenHashUpper));
            Assert.assertNull("Binary token_hash must remain case-sensitive on " + dbType, authDal.sessionMapper().queryByTokenHash(tokenHashLower));
            Assert.assertEquals(1, authDal.sessionMapper().deleteExpiredByExpire(2000L, 10));

            // 6. DmAiHistoryMapper#findCurrentTimeMillis
            long dbNowMs = aiDal.historyMapper().findCurrentTimeMillis();
            Assert.assertTrue("findCurrentTimeMillis should return valid epoch ms", dbNowMs > 1700000000000L);

            // 7. DmDsMetaDataMapper#insertOrUpdate (UPSERT) + deleteBeforeDate (batch DELETE LIMIT)
            dsDal.metaDataMapper().insertOrUpdate(InitSeedConstants.ADMIN_UID, 8801L, "/Db/Tbl_" + suffix, MetaInformationType.TableDetail, "{\"v\":1}");
            dsDal.metaDataMapper().insertOrUpdate(InitSeedConstants.ADMIN_UID, 8801L, "/db/tbl_" + suffix, MetaInformationType.TableDetail, "{\"v\":2}");
            DmDsMetaDataDO cachedMeta = dsDal.metaDataMapper().queryCache(InitSeedConstants.ADMIN_UID, 8801L, "/DB/TBL_" + suffix, MetaInformationType.TableDetail);
            Assert.assertNotNull(cachedMeta);
            Assert.assertEquals("{\"v\":2}", cachedMeta.getContext());
            int cleanedMeta = dsDal.metaDataMapper().deleteBeforeDate(new Date(System.currentTimeMillis() + 60000L));
            Assert.assertTrue(cleanedMeta >= 1);

            // 8. DmChangeTransferMapper#insertIgnore + keyColumn backfill + queryReadyList NULLS FIRST
            DmChangeTransferDO transferWithTime = new DmChangeTransferDO();
            transferWithTime.setOwnerUid(InitSeedConstants.ADMIN_UID);
            transferWithTime.setRefBatchId(100L);
            transferWithTime.setRefSourceFlowId(10L);
            transferWithTime.setRefSourceChangeId(201L + (System.nanoTime() % 100000L));
            transferWithTime.setRefTargetFlowId(20L);
            transferWithTime.setTransferStatus(ChangeTransferStatus.PENDING);
            transferWithTime.setScheduleTime(new Date(System.currentTimeMillis() - 10000L));
            transferWithTime.setTryTimes(0);
            Assert.assertEquals(1, changeFlowDal.transferMapper().insertIgnore(transferWithTime));
            Assert.assertNotNull(transferWithTime.getId());
            // Duplicate insertIgnore should return 0 and not throw
            Assert.assertEquals(0, changeFlowDal.transferMapper().insertIgnore(transferWithTime));

            DmChangeTransferDO transferImmediate = new DmChangeTransferDO();
            transferImmediate.setOwnerUid(InitSeedConstants.ADMIN_UID);
            transferImmediate.setRefBatchId(100L);
            transferImmediate.setRefSourceFlowId(10L);
            transferImmediate.setRefSourceChangeId(transferWithTime.getRefSourceChangeId() + 1L);
            transferImmediate.setRefTargetFlowId(20L);
            transferImmediate.setTransferStatus(ChangeTransferStatus.PENDING);
            transferImmediate.setScheduleTime(null);
            transferImmediate.setTryTimes(0);
            Assert.assertEquals(1, changeFlowDal.transferMapper().insertIgnore(transferImmediate));

            List<DmChangeTransferDO> readyList = changeFlowDal.transferMapper().queryReadyList(new Date(), 10);
            Assert.assertTrue(readyList.size() >= 2);
            Assert.assertNull("NULL schedule_time must be ordered first on " + dbType, readyList.get(0).getScheduleTime());

            // 9. DmChangeTriggerReceiptMapper#reserve (INSERT IGNORE / ON CONFLICT DO NOTHING)
            DmChangeTriggerReceiptDO receipt = new DmChangeTriggerReceiptDO();
            receipt.setOwnerUid(InitSeedConstants.ADMIN_UID);
            receipt.setRefFlowId(10L);
            receipt.setProvider(ScmType.Gitlab);
            receipt.setDeliveryId("deliv_" + suffix);
            receipt.setCommitId("commit_" + suffix);
            receipt.setTriggerType("WEBHOOK");
            Assert.assertEquals(1, changeFlowDal.triggerReceiptMapper().reserve(receipt));
            Assert.assertNotNull(receipt.getId());
            Assert.assertEquals(0, changeFlowDal.triggerReceiptMapper().reserve(receipt));

            // 10. DmApprovalProcessActivityMapper JSON_EXTRACT / jsonb ->> 'runId'
            DmApprovalProcessActivityDO activity = new DmApprovalProcessActivityDO();
            activity.setProcessId(7701L);
            activity.setTicketId(7702L);
            activity.setActivityId("PRE_INIT_" + suffix);
            activity.setActivityTitle("PreInit");
            activity.setOrderNumber(1);
            activity.setTaskStatus("RUNNING");
            activity.setContext("{\"runId\":\"run-" + suffix + "\",\"step\":1}");
            Assert.assertEquals(1, approvalDal.activityMapper().insert(activity));
            Assert.assertEquals(1, approvalDal.activityMapper().heartbeatPreInitTask(7701L, "PRE_INIT_" + suffix, "run-" + suffix));
            Assert.assertEquals(1, approvalDal.activityMapper().updatePreInitContext(7701L, "PRE_INIT_" + suffix, "run-" + suffix, "{\"runId\":\"run-" + suffix + "\",\"step\":2}"));
            Assert.assertEquals(1, approvalDal.activityMapper().completePreInitTask(7701L, "PRE_INIT_" + suffix, "SUCCESS", "run-" + suffix, "{\"runId\":\"run-" + suffix + "\",\"done\":true}"));

            // 11. SQL Script LIKE ... ESCAPE '=', PaginationInnerInterceptor, case-insensitive UNIQUE
            DmExecSqlScriptDO script = new DmExecSqlScriptDO();
            script.setOwnerUid(InitSeedConstants.ADMIN_UID);
            script.setName("My_Script_" + suffix);
            script.setDsType("PostgreSQL");
            script.setFileUri("local://script_" + suffix);
            script.setStatus(SqlScriptStatus.Ready);
            script.setVersion(1L);
            script.setGmtCreate(new Date());
            script.setGmtModified(new Date());
            Assert.assertEquals(1, executionDal.sqlScriptMapper().insert(script));

            PageObj pageObj = new PageObj();
            pageObj.setPageNum(1);
            pageObj.setPageSize(10);
            IPage<DmExecSqlScriptDO> scriptPage = executionDal.sqlScriptMapper().listByOwner(PageUtils.startPage(pageObj), InitSeedConstants.ADMIN_UID, "my=_script_=" + suffix, "PostgreSQL");
            Assert.assertEquals(1L, scriptPage.getTotal());

            DmExecSqlScriptDO dupScript = new DmExecSqlScriptDO();
            dupScript.setOwnerUid(InitSeedConstants.ADMIN_UID);
            dupScript.setName(("my_script_" + suffix).toLowerCase(Locale.ROOT));
            dupScript.setDsType("PostgreSQL");
            dupScript.setFileUri("local://script_dup_" + suffix);
            dupScript.setStatus(SqlScriptStatus.Ready);
            dupScript.setVersion(1L);
            dupScript.setGmtCreate(new Date());
            dupScript.setGmtModified(new Date());
            try {
                executionDal.sqlScriptMapper().insert(dupScript);
                Assert.fail("Expected case-insensitive DuplicateKeyException on " + dbType);
            } catch (DuplicateKeyException expected) {
                // expected
            }

            // 12. Boolean TRUE/FALSE & single-row UPDATE/DELETE without LIMIT 1 + gmt_modified trigger + BYTEA streaming
            DmSecSpecDO spec = new DmSecSpecDO();
            spec.setOwnerUid(InitSeedConstants.ADMIN_UID);
            spec.setName("Spec_" + suffix);
            spec.setDescription("init");
            spec.setEnable(false);
            Assert.assertEquals(1, secRuleDal.specMapper().insert(spec));
            Assert.assertEquals(1, secRuleDal.specMapper().enableSpec(InitSeedConstants.ADMIN_UID, spec.getId()));
            Assert.assertTrue(secRuleDal.specMapper().queryByIdAndUid(InitSeedConstants.ADMIN_UID, spec.getId()).isEnable());
            Assert.assertEquals(1, secRuleDal.specMapper().disableSpec(InitSeedConstants.ADMIN_UID, spec.getId()));
            Assert.assertFalse(secRuleDal.specMapper().queryByIdAndUid(InitSeedConstants.ADMIN_UID, spec.getId()).isEnable());
            Assert.assertEquals(1, secRuleDal.specMapper().deleteByUidAndId(InitSeedConstants.ADMIN_UID, spec.getId()));

            // Verify gmt_modified auto-update trigger when UPDATE omits gmt_modified
            Date beforeModified = authDal.userMapper().queryByUid(subUser.getUid()).getGmtModified();
            Thread.sleep(1100L);
            authDal.userMapper().updateUserName(subUser.getUid(), "SubUserUpdated_" + suffix);
            Date afterModified = authDal.userMapper().queryByUid(subUser.getUid()).getGmtModified();
            Assert.assertTrue("gmt_modified must auto-update on " + dbType, afterModified.after(beforeModified));

            // Verify BYTEA / LONGBLOB streaming read & write
            DmSysAttachmentDO att = new DmSysAttachmentDO();
            att.setOwnerUid(InitSeedConstants.ADMIN_UID);
            att.setApprovalId(uniqueResId);
            att.setAttachmentType(SysAttachmentType.SQL_FILE);
            att.setAttachmentStatus(SysAttachmentStatus.CONFIRMED);
            att.setFileName("test.sql");
            att.setFileSize(11L);
            Assert.assertEquals(1, systemDal.attachmentMapper().insert(att));
            byte[] payload = "SELECT 123;".getBytes(StandardCharsets.UTF_8);
            systemDal.writeAttachment(att.getId(), new ByteArrayInputStream(payload), payload.length);
            ByteArrayOutputStream readBack = new ByteArrayOutputStream();
            Assert.assertTrue(systemDal.readAttachment(att.getId(), readBack));
            Assert.assertArrayEquals(payload, readBack.toByteArray());

            // 13. DmExecSqlAuditMapper#deleteAuditBeforeDate (batch DELETE LIMIT with subquery)
            DmExecSqlAuditDO audit = new DmExecSqlAuditDO();
            audit.setUid(subUser.getUid());
            audit.setUserName(subUser.getUsername());
            audit.setDsId(8801L);
            audit.setQueryId("qid_" + suffix);
            audit.setSessionId("sid_" + suffix);
            audit.setWorkSeqNumber("wsn_" + suffix);
            audit.setRequester(Requester.CONSOLE);
            audit.setStatus(SqlStatus.SUCCESS);
            audit.setExecSql("SELECT 1");
            audit.setOriginalSql("SELECT 1");
            audit.setOperateTime(new Date(System.currentTimeMillis() - 120000L));
            audit.setEndTime(new Date(System.currentTimeMillis() - 60000L));
            Assert.assertEquals(1, executionDal.sqlAuditMapper().insert(audit));
            int deletedAudits = executionDal.sqlAuditMapper().deleteAuditBeforeDate(InitSeedConstants.ADMIN_UID, new Date());
            Assert.assertTrue("deleteAuditBeforeDate must delete expired audit rows on " + dbType, deletedAudits >= 1);

            // 14. Strict PG typing regressions: DmDsMapper envId(long), DmAuthUserMapper parentId(Long), DmExecAsyncTask processValue(Long)
            DmDsDO ds = new DmDsDO();
            ds.setUid(InitSeedConstants.ADMIN_UID);
            ds.setOwner(InitSeedConstants.ADMIN_UID);
            ds.setDataSourceType(DataSourceType.PostgreSQL);
            ds.setHost("127.0.0.1:15432");
            ds.setInstanceDesc("StrictTypeDs_" + suffix);
            ds.setInstanceId("ds_inst_" + suffix);
            ds.setLifeCycleState(LifeCycleState.CREATED);
            ds.setDsEnvId(1L);
            Assert.assertEquals(1, dsDal.dsMapper().insert(ds));
            Assert.assertFalse(dsDal.dsMapper().listByUidAndEnvId(InitSeedConstants.ADMIN_UID, 1L).isEmpty());
            Assert.assertFalse(dsDal.dsMapper().listByDsIdsAndEnvId(Collections.singletonList(ds.getId()), 1L).isEmpty());
            Assert.assertNotNull(dsDal.dsMapper().selectById(ds.getId()));

            Assert.assertNull(authDal.userMapper().queryBySubAccountAndBind(subUser.getParentId(), "missing_" + suffix, "DINGTALK"));

            DmExecAsyncTaskDO task = new DmExecAsyncTaskDO();
            task.setTitle("Async_" + suffix);
            task.setUid(InitSeedConstants.ADMIN_UID);
            task.setHandlerName("testHandler");
            task.setHandlerType("TEST");
            task.setShowInDock(false);
            task.setProcessType(AsyncTaskProcessType.PROGRESS);
            task.setProcessValue(10L);
            task.setFastFail(false);
            task.setStatus(AsyncTaskStatus.INIT);
            Assert.assertEquals(1, executionDal.asyncTaskMapper().insert(task));
            executionDal.asyncTaskMapper().updateProcess(task.getId(), AsyncTaskProcessType.PROGRESS.name(), 75L);
            DmExecAsyncTaskDO reloadedTask = executionDal.asyncTaskMapper().queryById(task.getId());
            Assert.assertEquals(Long.valueOf(75L), reloadedTask.getProcessValue());
        }
    }

    private static void runFlywayUpgrade(String jdbcUrl, String user, String pass) {
        try (ConfigurableApplicationContext ctx = startTaskContext(jdbcUrl, user, pass)) {
            ctx.getBean(DmFlywayInit.class).doUpgradeAndValidate();
        }
    }

    private static ConfigurableApplicationContext startTaskContext(String jdbcUrl, String user, String pass) {
        SpringApplication app = new SpringApplication(InitTaskApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setLazyInitialization(true);
        app.setDefaultProperties(buildDataSourceProps(jdbcUrl, user, pass));
        return app.run();
    }

    private static Properties buildDataSourceProps(String jdbcUrl, String user, String pass) {
        Properties props = new Properties();
        props.setProperty("spring.datasource.url", jdbcUrl);
        props.setProperty("spring.datasource.jdbcurl", jdbcUrl);
        props.setProperty("spring.datasource.username", user);
        props.setProperty("spring.datasource.password", pass == null ? "" : pass);
        props.setProperty("server.port", "-1");
        return props;
    }

    private static Map<String, Set<String>> loadTableColumns(String jdbcUrl, String user, String pass, String schema) throws SQLException {
        Map<String, Set<String>> result = new TreeMap<>();
        String sql = "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = ? AND table_name <> ? ORDER BY table_name, column_name";
        try (Connection conn = DmDalConfig.createDriverConnection(jdbcUrl, user, pass, 5000L);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, DmFlywayInit.TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString(1).toLowerCase(Locale.ROOT);
                    String col = rs.getString(2).toLowerCase(Locale.ROOT);
                    result.computeIfAbsent(table, k -> new TreeSet<>()).add(col);
                }
            }
        }
        return result;
    }

    private static void dropPgDatabaseQuietly(String maintenanceUrl, String user, String pass, String dbName) {
        try (Connection conn = DmDalConfig.createDriverConnection(maintenanceUrl, user, pass, 5000L);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP DATABASE IF EXISTS \"" + dbName.replace("\"", "\"\"") + "\" WITH (FORCE)");
        } catch (Exception ignored) {
        }
    }

    private static String replaceDatabaseName(String jdbcUrl, String newDbName) {
        int queryIdx = jdbcUrl.indexOf('?');
        String base = queryIdx >= 0 ? jdbcUrl.substring(0, queryIdx) : jdbcUrl;
        String query = queryIdx >= 0 ? jdbcUrl.substring(queryIdx) : "";
        int slashIdx = base.lastIndexOf('/');
        return base.substring(0, slashIdx + 1) + newDbName + query;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String resolveConfig(String sysProp, String envName) {
        String value = System.getProperty(sysProp);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        String env = System.getenv(envName);
        return env == null ? null : env.trim();
    }
}

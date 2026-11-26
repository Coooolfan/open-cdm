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
package com.clougence.clouddm.worker.component.workspace;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.duckdb.DuckDBFunctions;

import com.clougence.clouddm.api.common.GlobalConfUtils;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceQueryPlanDTO;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceSourcePlanDTO;
import com.clougence.clouddm.base.metadata.ds.ColMetaData;
import com.clougence.clouddm.base.metadata.ds.DataSourceConfig;
import com.clougence.clouddm.sdk.execute.meta.DsMetaService;
import com.clougence.clouddm.sdk.execute.resource.DsResourceManager;
import com.clougence.clouddm.sdk.execute.resultset.echo.ReceiveMode;
import com.clougence.clouddm.sdk.execute.session.*;
import com.clougence.clouddm.sdk.execute.session.ResultBuilder.*;
import com.clougence.clouddm.sdk.execute.session.rdb.RdbIsolation;
import com.clougence.clouddm.sdk.execute.session.result.ValueProcessService;
import com.clougence.clouddm.sdk.execute.session.result.fetcher.ValueFetcher;
import com.clougence.clouddm.worker.component.notify.SidecarSqlNotifyService;
import com.clougence.clouddm.worker.component.session.ssh.SshTunnelManager;
import com.clougence.clouddm.worker.component.workspace.udf.CdmRemoteQueryFunction;
import com.clougence.clouddm.worker.component.workspace.udf.CdmRemoteTableFunction;
import com.clougence.clouddm.worker.global.config.DmSidecarConfig;
import com.clougence.utils.StringUtils;
import com.clougence.utils.io.FileUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkspaceSession implements Session {

    public static final String                    REMOTE_TABLE_FUNCTION = "cdm_remote_table";
    public static final String                    REMOTE_QUERY_FUNCTION = "cdm_remote_query";

    private final String                          sessionId;
    private final DataSourceConfig                virtualDsConfig;
    private final DsMetaService                   metaService;
    private final Connection                      duckDbConnection;
    private final File                            sessionTempDir;
    private final WorkspaceExecutionContext       execContext           = new WorkspaceExecutionContext();
    private final DuckDbColReader                 colReader             = new DuckDbColReader();
    private final List<SessionCloseListener>      closeListeners        = new CopyOnWriteArrayList<>();
    private final AtomicBoolean                   executing             = new AtomicBoolean(false);
    private final AtomicBoolean                   cancelSignal          = new AtomicBoolean(false);
    private final AtomicBoolean                   closed                = new AtomicBoolean(false);
    private final int                             queryTimeoutSec;

    private volatile long                         lastQueryTime         = System.currentTimeMillis();
    private volatile String                       currentQueryId        = null;
    private volatile Statement                    currentStatement      = null;

    public WorkspaceSession(String sessionId, DsResourceManager rm, SshTunnelManager sshTunnelManager, ValueProcessService valueProcessService,
                            SidecarSqlNotifyService notifyService, DmSidecarConfig dmConfig) throws Exception {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId is null.");
        this.queryTimeoutSec = Math.max(10, dmConfig.getWorkspaceQueryTimeoutSec());

        this.virtualDsConfig = new DataSourceConfig();
        this.virtualDsConfig.setInstanceId("workspace_" + sessionId);

        this.sessionTempDir = createSessionTempDir(dmConfig, sessionId);
        Connection conn = null;
        try {
            Class.forName("org.duckdb.DuckDBDriver");
            conn = DriverManager.getConnection("jdbc:duckdb:");
            configureDuckDbEngine(conn, dmConfig, this.sessionTempDir);

            CdmRemoteTableFunction tableFn = new CdmRemoteTableFunction(this.execContext, rm, sshTunnelManager, valueProcessService, notifyService, this.sessionId,
                dmConfig.getWorkspaceMaxSourceRows(), this.queryTimeoutSec);
            CdmRemoteQueryFunction queryFn = new CdmRemoteQueryFunction(this.execContext, rm, sshTunnelManager, valueProcessService, notifyService, this.sessionId,
                dmConfig.getWorkspaceMaxSourceRows(), this.queryTimeoutSec);

            DuckDBFunctions.tableFunction()
                .withName(REMOTE_TABLE_FUNCTION)
                .withParameter(String.class)
                .withProjectionPushdown()
                .withFunction(tableFn)
                .register(conn);

            DuckDBFunctions.tableFunction()
                .withName(REMOTE_QUERY_FUNCTION)
                .withParameter(String.class)
                .withFunction(queryFn)
                .register(conn);

            lockDownDuckDbSandbox(conn);
            this.duckDbConnection = conn;
            this.metaService = new WorkspaceMetaService(readDuckDbVersion(conn));
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception closeErr) {
                    log.warn("Failed closing DuckDB connection after init failure: {}", closeErr.getMessage());
                }
            }
            cleanupTempDir(this.sessionTempDir);
            throw e;
        }
    }

    private static File createSessionTempDir(DmSidecarConfig dmConfig, String sessionId) throws IOException {
        String configuredBase = dmConfig.getWorkspaceTempDir();
        File baseDir;
        if (StringUtils.isNotBlank(configuredBase)) {
            baseDir = new File(configuredBase);
        } else {
            baseDir = new File(GlobalConfUtils.getTempDataHome(), "duckdb-workspace");
        }
        String safeId = sessionId.replaceAll("[^a-zA-Z0-9_-]", "_");
        File dir = new File(baseDir, "ws_" + safeId);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create workspace temp directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    private static void configureDuckDbEngine(Connection conn, DmSidecarConfig dmConfig, File tempDir) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (StringUtils.isNotBlank(dmConfig.getWorkspaceMemoryLimit())) {
                stmt.execute("SET memory_limit = '" + escapeSqlString(dmConfig.getWorkspaceMemoryLimit()) + "'");
            }
            if (dmConfig.getWorkspaceThreads() > 0) {
                stmt.execute("SET threads = " + dmConfig.getWorkspaceThreads());
            }
            stmt.execute("SET temp_directory = '" + escapeSqlString(tempDir.getAbsolutePath()) + "'");
            if (StringUtils.isNotBlank(dmConfig.getWorkspaceMaxTempDirSize())) {
                stmt.execute("SET max_temp_directory_size = '" + escapeSqlString(dmConfig.getWorkspaceMaxTempDirSize()) + "'");
            }
        }
    }

    private static void lockDownDuckDbSandbox(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET enable_external_access = false");
            stmt.execute("SET allow_community_extensions = false");
            stmt.execute("SET allow_unsigned_extensions = false");
            stmt.execute("SET lock_configuration = true");
        }
    }

    private static String readDuckDbVersion(Connection conn) {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT version()")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (Exception e) {
            log.warn("Failed to read DuckDB version: {}", e.getMessage());
        }
        return "2.0";
    }

    private static String escapeSqlString(String value) {
        return value.replace("'", "''");
    }

    @Override
    public String getSessionId() { return this.sessionId; }

    @Override
    public long getLastQueryTime() { return this.lastQueryTime; }

    @Override
    public DsMetaService getMetaService() { return this.metaService; }

    @Override
    public DataSourceConfig getDsConfig() { return this.virtualDsConfig; }

    @Override
    public boolean isExecuting() { return this.executing.get(); }

    @Override
    public String getCurrentQueryId() { return this.currentQueryId; }

    @Override
    public void addCloseListener(SessionCloseListener closeListener) {
        if (closeListener != null) {
            this.closeListeners.add(closeListener);
        }
    }

    @Override
    public void cancel() {
        this.cancelSignal.set(true);
        this.execContext.cancelAllStreams();
        Statement stmt = this.currentStatement;
        if (stmt != null) {
            try {
                stmt.cancel();
            } catch (Exception e) {
                log.warn("Failed to cancel DuckDB statement for session {}: {}", this.sessionId, e.getMessage());
            }
        }
    }

    @Override
    public void executeQuery(QueryRequest query, ResultBuilder builder) {
        this.lastQueryTime = System.currentTimeMillis();
        this.currentQueryId = query.getQueryId();
        this.cancelSignal.set(false);
        this.executing.set(true);

        long beginTime = System.currentTimeMillis();
        String sqlToExecute = query.getQueryBody();
        if (query instanceof WorkspaceQueryRequest wsReq && wsReq.getWorkspacePlan() != null) {
            WorkspaceQueryPlanDTO plan = wsReq.getWorkspacePlan();
            sqlToExecute = plan.getRewrittenDuckDbSql();
            Map<String, WorkspaceSourcePlanDTO> sourceMap = new LinkedHashMap<>();
            if (plan.getSources() != null) {
                for (WorkspaceSourcePlanDTO src : plan.getSources()) {
                    sourceMap.put(src.getRefId(), src);
                }
            }
            this.execContext.loadPlans(sourceMap);
        } else {
            this.execContext.loadPlans(Collections.emptyMap());
        }

        try (Statement stmt = this.duckDbConnection.createStatement()) {
            this.currentStatement = stmt;
            try {
                stmt.setQueryTimeout(this.queryTimeoutSec);
            } catch (Exception ignored) {
                // DuckDB JDBC may not support Statement.setQueryTimeout in all builds.
            }

            boolean hasResultSet = stmt.execute(sqlToExecute);
            if (hasResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    String resultId = builder.newResultId(query);
                    receiveResultRows(query, resultId, rs, builder);
                }
            } else {
                ResultCountBuild cb = builder.newResultCount(query);
                try {
                    long updateCount = stmt.getLargeUpdateCount();
                    cb.receiveUpdateCount(Math.max(0L, updateCount));
                    cb.collectCost(System.currentTimeMillis() - beginTime);
                    cb.finishRecord(true);
                } catch (Exception e) {
                    cb.collectCost(System.currentTimeMillis() - beginTime);
                    cb.finishRecord(false, e.getMessage(), e);
                    throw e;
                }
            }
        } catch (Throwable e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            ResultMessageBuild mb = builder.newMessage(query);
            mb.receiveMessage(MessageLevel.Error, errMsg);
            mb.collectCost(System.currentTimeMillis() - beginTime);
            mb.finishRecord(false, errMsg, e);
            throw new RuntimeException(errMsg, e);
        } finally {
            this.currentStatement = null;
            this.execContext.clear();
            this.executing.set(false);
            this.lastQueryTime = System.currentTimeMillis();
        }
    }

    private void receiveResultRows(QueryRequest query, String resultId, ResultSet rs, ResultBuilder builder) throws SQLException, IOException {
        int fetchCount = 0;
        long beginFetchTimeMs = System.currentTimeMillis();
        long lastFetchTimeMs = beginFetchTimeMs;
        QueryResultConf resultConf = query.getResultConf() != null ? query.getResultConf() : new QueryResultConf();
        long maxFetchCountLimit = resultConf.getFetchRecordCountLimit();
        long maxPageSize = resultConf.getFetchPageSize();
        ReceiveMode receiveMode = resultConf.getReceiveMode() != null ? resultConf.getReceiveMode() : ReceiveMode.PAGINATED;

        ResultSetMetaBuild meta = builder.newResultMeta(query, resultId);
        ResultSetRowsBuild rb = meta.receiveMeta(extractMetaData(rs.getMetaData()));
        try {
            meta.finishRecord(true);
            boolean silentReceive = false;
            long lastPageFetchCount = 0;

            while (!this.cancelSignal.get() && rs.next()) {
                rb.receiveRow(silentReceive, rs);
                fetchCount++;
                lastPageFetchCount++;

                switch (receiveMode) {
                    case STREAM: {
                        rb.collectMetric(fetchCount);
                        rb.collectCost(System.currentTimeMillis() - beginFetchTimeMs);
                        rb.flushData();
                        rb.finishAndContinue();
                        lastPageFetchCount = 0;
                        break;
                    }
                    case PAGINATED:
                    case PAGE_FULL: {
                        if (!silentReceive && maxPageSize > 0 && lastPageFetchCount >= maxPageSize) {
                            rb.collectCost(System.currentTimeMillis() - beginFetchTimeMs);
                            rb.collectMetric(fetchCount);
                            rb.flushData();
                            rb.finishAndContinue();
                            lastPageFetchCount = 0;
                            silentReceive = receiveMode == ReceiveMode.PAGINATED;
                        }
                        if (silentReceive && (lastFetchTimeMs + 1500) < System.currentTimeMillis()) {
                            lastFetchTimeMs = System.currentTimeMillis();
                            ResultSetRowCountUpdateBuild cb = rb.newRowCountUpdate();
                            cb.collectCost(System.currentTimeMillis() - beginFetchTimeMs);
                            cb.collectMetric(fetchCount);
                            cb.finishRecord(true);
                            rb.flushData();
                        }
                        break;
                    }
                    default:
                        throw new SQLException("Unsupported receive mode: " + receiveMode);
                }

                if (maxFetchCountLimit > 0 && fetchCount >= maxFetchCountLimit) {
                    ResultMessageBuild mb = builder.newMessage(query);
                    mb.receiveMessage(MessageLevel.Warn, "maximum number of receive rows limit " + maxFetchCountLimit + ", other rows will be skipped.");
                    mb.finishRecord(true);
                    break;
                }
            }

            if (silentReceive) {
                ResultSetRowCountUpdateBuild cb = rb.newRowCountUpdate();
                cb.collectMetric(fetchCount);
                cb.collectCost(System.currentTimeMillis() - beginFetchTimeMs);
                cb.finishRecord(true);
                rb.flushData();
                rb.finishAndSilent(true);
                ResultMessageBuild mb = builder.newMessage(query);
                mb.receiveMessage(MessageLevel.Info, fetchCount + " row retrieved finish.");
                mb.finishRecord(true);
            } else {
                rb.collectMetric(fetchCount);
                rb.collectCost(System.currentTimeMillis() - beginFetchTimeMs);
                rb.flushData();
                rb.finishRecord(true);
            }
        } catch (Exception e) {
            rb.collectCost(System.currentTimeMillis() - beginFetchTimeMs);
            rb.finishRecord(false, e.getMessage(), e);
            throw e;
        }
    }

    private Map<String, ResultColMeta> extractMetaData(ResultSetMetaData metaData) throws SQLException {
        Map<String, ResultColMeta> mapping = new LinkedHashMap<>();
        int count = metaData.getColumnCount();
        for (int i = 1; i <= count; i++) {
            String label = metaData.getColumnLabel(i);
            if (StringUtils.isBlank(label)) {
                label = metaData.getColumnName(i);
            }
            if (StringUtils.isBlank(label)) {
                label = "col_" + i;
            }
            String uniqueName = label;
            int suffix = 1;
            while (mapping.containsKey(uniqueName)) {
                uniqueName = label + "_" + suffix++;
            }

            ColMetaData cm = new ColMetaData();
            cm.setIndex(i);
            cm.setColumn(uniqueName);
            cm.setColumnType(metaData.getColumnTypeName(i));
            cm.setPrecision(metaData.getPrecision(i));
            try {
                cm.setJdbcType(JDBCType.valueOf(metaData.getColumnType(i)));
            } catch (Exception ignored) {
                cm.setJdbcType(JDBCType.VARCHAR);
            }

            ValueFetcher fetcher = this.colReader.readColumn(uniqueName, cm);
            mapping.put(uniqueName, new ResultColMeta(cm, fetcher));
        }
        return mapping;
    }

    @Override
    public <V> V executeQuery(SessionCallback<V> callback) throws Exception {
        throw new UnsupportedOperationException("SessionCallback is not supported on WorkspaceSession.");
    }

    @Override
    public void commit() {
    }

    @Override
    public void rollback() {
    }

    @Override
    public boolean isAutoCommit() { return true; }

    @Override
    public void setAutoCommit(boolean autoCommit) {
    }

    @Override
    public RdbIsolation getIsolation() { return null; }

    @Override
    public void setIsolation(RdbIsolation isolation) {
    }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public void setReadOnly(boolean readOnly) {
    }

    @Override
    public boolean hasUnCommitted() { return false; }

    @Override
    public void setCurrentCatalog(String catalog) {
    }

    @Override
    public void setCurrentSchema(String schemaName) {
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        cancel();
        this.execContext.clear();
        try {
            if (this.duckDbConnection != null && !this.duckDbConnection.isClosed()) {
                this.duckDbConnection.close();
            }
        } catch (Exception e) {
            log.warn("Failed closing DuckDB connection for workspace session {}: {}", this.sessionId, e.getMessage());
        }
        cleanupTempDir(this.sessionTempDir);

        for (SessionCloseListener listener : this.closeListeners) {
            try {
                listener.onClose(this.sessionId);
            } catch (Exception e) {
                log.warn("Workspace session close listener error: {}", e.getMessage());
            }
        }
    }

    private static void cleanupTempDir(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        try {
            FileUtils.deleteDirectory(dir);
        } catch (Exception e) {
            log.warn("Failed deleting workspace temp directory {}: {}", dir.getAbsolutePath(), e.getMessage());
        }
    }
}

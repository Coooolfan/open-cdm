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

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.clougence.clouddm.api.sidecar.session.drivers.DriverRef;
import com.clougence.clouddm.api.sidecar.session.drivers.DriverUtils;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceSourcePlanDTO;
import com.clougence.clouddm.base.metadata.ds.DataSourceConfig;
import com.clougence.clouddm.platform.plugin.DsPluginInfo;
import com.clougence.clouddm.platform.plugin.PluginManager;
import com.clougence.clouddm.sdk.execute.resource.DsResourceManager;
import com.clougence.clouddm.sdk.execute.resultset.echo.ReceiveMode;
import com.clougence.clouddm.sdk.execute.resultset.echo.ResultPhase;
import com.clougence.clouddm.sdk.execute.resultset.echo.ResultPhaseType;
import com.clougence.clouddm.sdk.execute.resultset.echo.ResultType;
import com.clougence.clouddm.sdk.execute.session.*;
import com.clougence.clouddm.sdk.execute.session.result.ValueProcessService;
import com.clougence.clouddm.worker.component.notify.SidecarSqlNotifyService;
import com.clougence.clouddm.worker.component.session.ssh.SshTunnelHandle;
import com.clougence.clouddm.worker.component.session.ssh.SshTunnelManager;
import com.clougence.utils.io.IOUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkspaceSourceStream implements AutoCloseable {

    private static final Object[]                                   EOF_MARKER    = new Object[0];
    private static final int                                        QUEUE_SIZE    = 4096;

    private final WorkspaceSourcePlanDTO                            plan;
    private final String                                            overrideSql;
    private final DsResourceManager                                 rm;
    private final SshTunnelManager                                  sshTunnelManager;
    private final ValueProcessService                               valueProcessService;
    private final SidecarSqlNotifyService                           notifyService;
    private final String                                            workspaceSessionId;
    private final WorkspaceExecutionContext                         execContext;
    private final long                                              maxRows;
    private final int                                               timeoutSec;
    private final boolean                                           metadataOnly;

    private final CompletableFuture<List<WorkspaceSourceColumn>>    metaFuture    = new CompletableFuture<>();
    private final ArrayBlockingQueue<Object[]>                      queue         = new ArrayBlockingQueue<>(QUEUE_SIZE);
    private final AtomicReference<Throwable>                        errorRef      = new AtomicReference<>();
    private final AtomicBoolean                                     cancelled     = new AtomicBoolean(false);
    private final AtomicBoolean                                     eofReached    = new AtomicBoolean(false);
    private final AtomicBoolean                                     closed        = new AtomicBoolean(false);
    private volatile Session                                        remoteSession = null;
    private volatile SshTunnelHandle                                sshTunnel     = null;
    private volatile Thread                                         producerThread;

    public WorkspaceSourceStream(WorkspaceSourcePlanDTO plan, String overrideSql, DsResourceManager rm, SshTunnelManager sshTunnelManager,
                                 ValueProcessService valueProcessService, SidecarSqlNotifyService notifyService, String workspaceSessionId,
                                 WorkspaceExecutionContext execContext, long maxRows, int timeoutSec, boolean metadataOnly){
        this.plan = plan;
        this.overrideSql = overrideSql;
        this.rm = rm;
        this.sshTunnelManager = sshTunnelManager;
        this.valueProcessService = valueProcessService;
        this.notifyService = notifyService;
        this.workspaceSessionId = workspaceSessionId;
        this.execContext = execContext;
        this.maxRows = maxRows;
        this.timeoutSec = timeoutSec;
        this.metadataOnly = metadataOnly;
    }

    public void start() {
        this.execContext.registerStream(this);
        this.rm.getExecutor(this.plan.getDsConfig()).execute(this::runProducer);
    }

    private void runProducer() {
        this.producerThread = Thread.currentThread();
        try {
            DataSourceConfig dsConfig = this.plan.getDsConfig();
            DsPluginInfo pluginInfo = PluginManager.findDsPlugin(dsConfig.getDataSourceType());
            if (pluginInfo == null) {
                throw new UnsupportedOperationException("No datasource plugin found for dsType '" + dsConfig.getDataSourceType() + "'.");
            }

            if (this.sshTunnelManager != null && this.sshTunnelManager.isEnabled(dsConfig)) {
                this.sshTunnel = this.sshTunnelManager.open(dsConfig);
                dsConfig = this.sshTunnel.getDsConfig();
            }

            SessionContextDTO ctx = this.plan.getSessionContext() != null ? this.plan.getSessionContext() : new SessionContextDTO();
            ctx.setSessionId("ws_src_" + UUID.randomUUID().toString().replace("-", ""));
            ctx.setRdbAutoCommit(true);
            ctx.setRdbReadOnly(true);
            ctx.setMaxIdleTimeSec(Math.max(this.timeoutSec, 60));

            DriverRef driverRef = DriverUtils.parseDriverRef(dsConfig.getDriverVersion());
            SessionFactory factory = pluginInfo.createSessionFactory(driverRef.getDriverFamily(), driverRef.getDriverVersion());
            this.remoteSession = factory.createSession(this.rm, dsConfig, ctx);

            QueryRequest req = buildExecQueryRequest();
            if (!this.metadataOnly && this.notifyService != null && this.plan.getSourceQueryRequest() != null) {
                this.notifyService.beginForQuery(this.plan.getSourceQueryRequest(), this.workspaceSessionId);
            }
            WorkspaceSourceResultBuilder builder = new WorkspaceSourceResultBuilder(this, this.valueProcessService, this.maxRows, this.metadataOnly);
            this.remoteSession.executeQuery(req, builder);

            if (!this.metaFuture.isDone()) {
                this.metaFuture.complete(Collections.emptyList());
            }
            reportSourceAuditFinish(true, null);
        } catch (Throwable e) {
            if (!this.cancelled.get()) {
                log.error("Workspace source stream failed for ds '{}': {}", this.plan.getDsName(), e.getMessage(), e);
                fail(e);
            }
            reportSourceAuditFinish(false, e.getMessage());
        } finally {
            signalEnd();
            closeResourcesQuietly();
            this.execContext.unregisterStream(this);
        }
    }

    private QueryRequest buildExecQueryRequest() {
        QueryRequest base = this.plan.getSourceQueryRequest();
        QueryRequest req = base != null ? base.clone() : new QueryRequest();
        req.setQueryId("wsq_" + UUID.randomUUID().toString().replace("-", ""));
        if (this.overrideSql != null) {
            req.setQueryBody(this.overrideSql);
        }
        QueryResultConf conf = req.getResultConf();
        if (conf == null) {
            conf = new QueryResultConf();
            req.setResultConf(conf);
        }
        conf.setCacheResult(false);
        conf.setReceiveMode(ReceiveMode.STREAM);
        conf.setFetchRecordCountLimit(this.maxRows > 0 ? this.maxRows + 1 : -1);
        conf.setFetchMoreResult(false);
        return req;
    }

    public void completeMeta(List<WorkspaceSourceColumn> columns) {
        this.metaFuture.complete(columns);
    }

    public List<WorkspaceSourceColumn> awaitColumns() throws Exception {
        try {
            return this.metaFuture.get(Math.max(this.timeoutSec, 10), TimeUnit.SECONDS);
        } catch (Exception e) {
            Throwable err = this.errorRef.get();
            if (err != null) {
                throw wrapException(err);
            }
            throw e;
        }
    }

    public void offerRow(Object[] row) throws InterruptedException {
        if (this.cancelled.get() || this.metadataOnly) {
            return;
        }
        while (!this.cancelled.get()) {
            if (this.queue.offer(row, 200, TimeUnit.MILLISECONDS)) {
                return;
            }
        }
    }

    public Object[] nextRow() throws Exception {
        while (true) {
            Throwable err = this.errorRef.get();
            if (err != null) {
                throw wrapException(err);
            }
            if (this.cancelled.get() || this.eofReached.get()) {
                return null;
            }
            Object[] item = this.queue.poll(200, TimeUnit.MILLISECONDS);
            if (item == null) {
                continue;
            }
            if (item == EOF_MARKER) {
                this.eofReached.set(true);
                Throwable endErr = this.errorRef.get();
                if (endErr != null) {
                    throw wrapException(endErr);
                }
                return null;
            }
            return item;
        }
    }

    public void fail(Throwable error) {
        if (error != null) {
            this.errorRef.compareAndSet(null, error);
            this.metaFuture.completeExceptionally(error);
            signalEnd();
        }
    }

    public boolean isCancelled() { return this.cancelled.get(); }

    public void cancel() {
        this.cancelled.set(true);
        Session session = this.remoteSession;
        if (session != null) {
            try {
                session.cancel();
            } catch (Exception ignored) {
            }
        }
        Thread t = this.producerThread;
        if (t != null) {
            t.interrupt();
        }
        this.queue.clear();
        signalEnd();
    }

    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            cancel();
            closeResourcesQuietly();
            this.execContext.unregisterStream(this);
        }
    }

    private void signalEnd() {
        if (!this.queue.offer(EOF_MARKER)) {
            this.queue.poll();
            this.queue.offer(EOF_MARKER);
        }
    }

    private void reportSourceAuditFinish(boolean success, String errMsg) {
        if (this.metadataOnly || this.notifyService == null || this.plan.getSourceQueryRequest() == null) {
            return;
        }
        try {
            ResultPhase phase = new ResultPhase();
            phase.setResultType(ResultType.Phase);
            phase.setPhaseType(ResultPhaseType.After);
            phase.setSessionId(this.workspaceSessionId);
            phase.setQueryId(this.plan.getSourceQueryRequest().getQueryId());
            phase.setSuccess(success);
            phase.setMessage(errMsg);
            this.notifyService.finishForQuery(this.plan.getSourceQueryRequest(), phase, false);
        } catch (Exception e) {
            log.warn("Failed reporting workspace source audit finish: {}", e.getMessage());
        }
    }

    private void closeResourcesQuietly() {
        Session session = this.remoteSession;
        this.remoteSession = null;
        IOUtils.closeQuietly(session);

        SshTunnelHandle tunnel = this.sshTunnel;
        this.sshTunnel = null;
        IOUtils.closeQuietly(tunnel);
    }

    private static Exception wrapException(Throwable err) {
        if (err instanceof Exception ex) {
            return ex;
        }
        return new RuntimeException(err.getMessage(), err);
    }
}

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
package com.clougence.clouddm.worker.component.workspace.udf;

import java.util.List;

import org.duckdb.DuckDBDataChunkWriter;
import org.duckdb.DuckDBTableFunction;
import org.duckdb.DuckDBTableFunctionBindInfo;
import org.duckdb.DuckDBTableFunctionCallInfo;
import org.duckdb.DuckDBTableFunctionInitInfo;
import org.duckdb.DuckDBValue;

import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceSourcePlanDTO;
import com.clougence.clouddm.sdk.execute.resource.DsResourceManager;
import com.clougence.clouddm.sdk.execute.session.result.ValueProcessService;
import com.clougence.clouddm.worker.component.notify.SidecarSqlNotifyService;
import com.clougence.clouddm.worker.component.session.ssh.SshTunnelManager;
import com.clougence.clouddm.worker.component.workspace.WorkspaceExecutionContext;
import com.clougence.clouddm.worker.component.workspace.WorkspaceSourceColumn;
import com.clougence.clouddm.worker.component.workspace.WorkspaceSourceStream;
import com.clougence.clouddm.worker.component.workspace.WorkspaceTypeMapper;

public class CdmRemoteQueryFunction implements DuckDBTableFunction<WorkspaceQueryBindData, WorkspaceQueryInitData, Object> {

    private final WorkspaceExecutionContext execContext;
    private final DsResourceManager         rm;
    private final SshTunnelManager          sshTunnelManager;
    private final ValueProcessService       valueProcessService;
    private final SidecarSqlNotifyService   notifyService;
    private final String                    workspaceSessionId;
    private final long                      maxRows;
    private final int                       timeoutSec;

    public CdmRemoteQueryFunction(WorkspaceExecutionContext execContext, DsResourceManager rm, SshTunnelManager sshTunnelManager,
                                  ValueProcessService valueProcessService, SidecarSqlNotifyService notifyService, String workspaceSessionId,
                                  long maxRows, int timeoutSec){
        this.execContext = execContext;
        this.rm = rm;
        this.sshTunnelManager = sshTunnelManager;
        this.valueProcessService = valueProcessService;
        this.notifyService = notifyService;
        this.workspaceSessionId = workspaceSessionId;
        this.maxRows = maxRows;
        this.timeoutSec = timeoutSec;
    }

    @Override
    public WorkspaceQueryBindData bind(DuckDBTableFunctionBindInfo info) throws Exception {
        String refId;
        try (DuckDBValue param = info.getParameter(0)) {
            refId = param.getString();
        }
        WorkspaceSourcePlanDTO plan = this.execContext.getPlan(refId);

        WorkspaceSourceStream stream = new WorkspaceSourceStream(plan, null, this.rm, this.sshTunnelManager, this.valueProcessService, this.notifyService,
            this.workspaceSessionId, this.execContext, this.maxRows, this.timeoutSec, false);
        try {
            stream.start();
            List<WorkspaceSourceColumn> columns = stream.awaitColumns();
            if (columns == null || columns.isEmpty()) {
                stream.close();
                throw new IllegalStateException("Remote #query(...) returned no result set columns for datasource: " + plan.getDsName());
            }
            for (WorkspaceSourceColumn col : columns) {
                info.addResultColumn(col.getName(), col.getDuckDbType());
            }
            return new WorkspaceQueryBindData(plan, columns, stream);
        } catch (Exception e) {
            stream.close();
            throw e;
        }
    }

    @Override
    public WorkspaceQueryInitData init(DuckDBTableFunctionInitInfo info) throws Exception {
        info.setMaxThreads(1);
        WorkspaceQueryBindData bindData = info.getBindData();
        WorkspaceSourceStream stream = bindData.claimInitialStream();
        if (stream == null) {
            stream = new WorkspaceSourceStream(bindData.getPlan(), null, this.rm, this.sshTunnelManager, this.valueProcessService, this.notifyService,
                this.workspaceSessionId, this.execContext, this.maxRows, this.timeoutSec, false);
            stream.start();
            stream.awaitColumns();
        }
        return new WorkspaceQueryInitData(stream, bindData.getColumns());
    }

    @Override
    public long apply(DuckDBTableFunctionCallInfo info, DuckDBDataChunkWriter output) throws Exception {
        WorkspaceQueryInitData initData = info.getInitData();
        WorkspaceSourceStream stream = initData.getStream();
        List<WorkspaceSourceColumn> columns = initData.getColumns();
        long outCols = output.columnCount();

        long rowIdx = 0;
        long capacity = output.capacity();
        while (rowIdx < capacity) {
            Object[] row = stream.nextRow();
            if (row == null) {
                break;
            }
            long copyCols = Math.min(outCols, Math.min(row.length, columns.size()));
            for (long col = 0; col < copyCols; col++) {
                WorkspaceTypeMapper.writeValue(output.vector(col), rowIdx, row[(int) col], columns.get((int) col).getDuckDbType());
            }
            rowIdx++;
        }
        return rowIdx;
    }
}

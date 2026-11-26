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

import java.util.ArrayList;
import java.util.List;

import org.duckdb.DuckDBColumnType;
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
import com.clougence.utils.StringUtils;

public class CdmRemoteTableFunction implements DuckDBTableFunction<WorkspaceTableBindData, WorkspaceTableInitData, Object> {

    private final WorkspaceExecutionContext execContext;
    private final DsResourceManager         rm;
    private final SshTunnelManager          sshTunnelManager;
    private final ValueProcessService       valueProcessService;
    private final SidecarSqlNotifyService   notifyService;
    private final String                    workspaceSessionId;
    private final long                      maxRows;
    private final int                       timeoutSec;

    public CdmRemoteTableFunction(WorkspaceExecutionContext execContext, DsResourceManager rm, SshTunnelManager sshTunnelManager,
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
    public WorkspaceTableBindData bind(DuckDBTableFunctionBindInfo info) throws Exception {
        String refId;
        try (DuckDBValue param = info.getParameter(0)) {
            refId = param.getString();
        }
        WorkspaceSourcePlanDTO plan = this.execContext.getPlan(refId);
        String probeSql = "SELECT * FROM " + plan.getQualifiedTable() + " WHERE 1 = 0";

        List<WorkspaceSourceColumn> columns;
        try (WorkspaceSourceStream probeStream = new WorkspaceSourceStream(plan, probeSql, this.rm, this.sshTunnelManager, this.valueProcessService, null,
            this.workspaceSessionId, this.execContext, 1, this.timeoutSec, true)) {
            probeStream.start();
            columns = probeStream.awaitColumns();
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalStateException("Remote table returned no column metadata: " + plan.getQualifiedTable());
        }

        for (WorkspaceSourceColumn col : columns) {
            info.addResultColumn(col.getName(), col.getDuckDbType());
        }
        return new WorkspaceTableBindData(plan, columns);
    }

    @Override
    public WorkspaceTableInitData init(DuckDBTableFunctionInitInfo info) throws Exception {
        info.setMaxThreads(1);
        WorkspaceTableBindData bindData = info.getBindData();
        WorkspaceSourcePlanDTO plan = bindData.getPlan();
        List<WorkspaceSourceColumn> allColumns = bindData.getColumns();

        long colCount = info.getColumnCount();
        List<String> projectedSelectCols = new ArrayList<>();
        List<DuckDBColumnType> projectedTypes = new ArrayList<>();
        boolean dummyCountOnly = true;

        for (long i = 0; i < colCount; i++) {
            long colIdx = info.getColumnIndex(i);
            if (colIdx >= 0 && colIdx < allColumns.size()) {
                WorkspaceSourceColumn col = allColumns.get((int) colIdx);
                projectedSelectCols.add(quoteIdentifier(col.getName(), plan.getQuoteLeft(), plan.getQuoteRight()));
                projectedTypes.add(col.getDuckDbType());
                dummyCountOnly = false;
            }
        }

        if (projectedSelectCols.isEmpty()) {
            // E.g. SELECT COUNT(*) FROM #ds.table: project only the first column to minimize remote transfer.
            WorkspaceSourceColumn firstCol = allColumns.get(0);
            projectedSelectCols.add(quoteIdentifier(firstCol.getName(), plan.getQuoteLeft(), plan.getQuoteRight()));
            projectedTypes.add(firstCol.getDuckDbType());
            dummyCountOnly = true;
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(String.join(", ", projectedSelectCols)).append(" FROM ").append(plan.getQualifiedTable());
        if (StringUtils.isNotBlank(plan.getFilterExpr())) {
            sql.append(" WHERE (").append(plan.getFilterExpr()).append(")");
        }

        WorkspaceSourceStream stream = new WorkspaceSourceStream(plan, sql.toString(), this.rm, this.sshTunnelManager, this.valueProcessService, this.notifyService,
            this.workspaceSessionId, this.execContext, this.maxRows, this.timeoutSec, false);
        stream.start();
        stream.awaitColumns();
        return new WorkspaceTableInitData(stream, projectedTypes, dummyCountOnly);
    }

    @Override
    public long apply(DuckDBTableFunctionCallInfo info, DuckDBDataChunkWriter output) throws Exception {
        WorkspaceTableInitData initData = info.getInitData();
        WorkspaceSourceStream stream = initData.getStream();
        List<DuckDBColumnType> types = initData.getProjectedTypes();
        long outCols = output.columnCount();

        long rowIdx = 0;
        long capacity = output.capacity();
        while (rowIdx < capacity) {
            Object[] row = stream.nextRow();
            if (row == null) {
                break;
            }
            if (!initData.isDummyCountProjection()) {
                long copyCols = Math.min(outCols, Math.min(row.length, types.size()));
                for (long col = 0; col < copyCols; col++) {
                    WorkspaceTypeMapper.writeValue(output.vector(col), rowIdx, row[(int) col], types.get((int) col));
                }
            }
            rowIdx++;
        }
        return rowIdx;
    }

    private static String quoteIdentifier(String ident, String left, String right) {
        String ql = StringUtils.isBlank(left) ? "\"" : left;
        String qr = StringUtils.isBlank(right) ? "\"" : right;
        String escaped = ident.replace(qr, qr + qr);
        return ql + escaped + qr;
    }
}

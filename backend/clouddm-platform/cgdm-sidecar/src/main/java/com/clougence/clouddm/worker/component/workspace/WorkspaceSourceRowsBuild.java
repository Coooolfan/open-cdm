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

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import com.clougence.clouddm.base.metadata.ds.ColMetaData;
import com.clougence.clouddm.sdk.execute.session.QueryRequest;
import com.clougence.clouddm.sdk.execute.session.ResultBuilder;
import com.clougence.clouddm.sdk.execute.session.ResultColMeta;
import com.clougence.clouddm.sdk.execute.session.result.ReaderOptions;
import com.clougence.clouddm.sdk.execute.session.result.ValueProcessService;
import com.clougence.clouddm.sdk.execute.session.result.fetcher.ValueFetcher;
import com.clougence.clouddm.sdk.execute.session.result.fetcher.ValueFetcherContext;

public class WorkspaceSourceRowsBuild implements ResultBuilder.ResultSetRowsBuild {

    private final WorkspaceSourceStream       stream;
    private final QueryRequest                queryRequest;
    private final ValueProcessService         valueProcessService;
    private final long                        maxRows;
    private final List<WorkspaceSourceColumn> columns;
    private final ValueFetcherContext[]       fetcherContexts;
    private final Map<String, ColMetaData>    rowMetaMap;
    private final Map<String, Object>         maskFlash = new HashMap<>();
    private final boolean                     maskingActive;
    private long                              rowCount  = 0;

    public WorkspaceSourceRowsBuild(WorkspaceSourceStream stream, QueryRequest queryRequest, ValueProcessService valueProcessService, long maxRows,
                                    List<WorkspaceSourceColumn> columns, ReaderOptions readerOptions){
        this.stream = stream;
        this.queryRequest = queryRequest;
        this.valueProcessService = valueProcessService;
        this.maxRows = maxRows;
        this.columns = columns;
        this.fetcherContexts = new ValueFetcherContext[columns.size()];
        this.rowMetaMap = new LinkedHashMap<>();

        for (int i = 0; i < columns.size(); i++) {
            WorkspaceSourceColumn col = columns.get(i);
            ResultColMeta colMeta = new ResultColMeta(col.getColMetaData(), col.getValueFetcher());
            this.fetcherContexts[i] = new ValueFetcherContext("ws_" + i, colMeta, readerOptions);
            this.rowMetaMap.put(col.getName(), col.getColMetaData());
        }

        boolean active = false;
        if (this.valueProcessService != null && this.queryRequest != null && this.queryRequest.getDsId() != null) {
            this.valueProcessService.begin(this.queryRequest, this.rowMetaMap, this.maskFlash);
            active = true;
        }
        this.maskingActive = active;
    }

    @Override
    public void collectMetric(int fetchCount) {
    }

    @Override
    public void receiveRow(boolean silent, ResultSet rs) throws SQLException, IOException {
        checkRowLimitAndCancel();
        Object[] rowValues = new Object[this.columns.size()];
        for (int i = 0; i < this.columns.size(); i++) {
            WorkspaceSourceColumn col = this.columns.get(i);
            ValueFetcher fetcher = col.getValueFetcher();
            ValueFetcherContext ctx = this.fetcherContexts[i];
            try {
                rowValues[i] = WorkspaceTypeMapper.readFromResultSet(rs, col.getName(), fetcher, ctx);
                if (ctx.isErrStatus()) {
                    throw new SQLException("Workspace source value read error: " + ctx.getErrObject());
                }
            } finally {
                ctx.free();
            }
        }
        applyMaskingAndOffer(rowValues);
    }

    @Override
    public void receiveRow(boolean silent, Map<String, Object> rs) throws SQLException, IOException {
        checkRowLimitAndCancel();
        Object[] rowValues = new Object[this.columns.size()];
        for (int i = 0; i < this.columns.size(); i++) {
            WorkspaceSourceColumn col = this.columns.get(i);
            rowValues[i] = rs == null ? null : rs.get(col.getName());
        }
        applyMaskingAndOffer(rowValues);
    }

    private void checkRowLimitAndCancel() throws SQLException {
        if (this.stream.isCancelled()) {
            throw new SQLException("Workspace source query cancelled.");
        }
        this.rowCount++;
        if (this.maxRows > 0 && this.rowCount > this.maxRows) {
            throw new SQLException("Workspace source row limit exceeded (max=" + this.maxRows + "). Add #filter(...) or #query(...) to narrow the source data.");
        }
    }

    private void applyMaskingAndOffer(Object[] rowValues) throws SQLException {
        if (this.maskingActive) {
            List<String> strRow = new ArrayList<>(rowValues.length);
            for (Object val : rowValues) {
                strRow.add(WorkspaceTypeMapper.toDisplayString(val));
            }
            List<String> masked = this.valueProcessService.processRow(this.queryRequest, this.rowMetaMap, strRow, this.maskFlash);
            if (masked != null) {
                for (int i = 0; i < Math.min(rowValues.length, masked.size()); i++) {
                    String before = strRow.get(i);
                    String after = masked.get(i);
                    if (!Objects.equals(before, after)) {
                        rowValues[i] = after;
                    }
                }
            }
        }
        try {
            this.stream.offerRow(rowValues);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Workspace source stream interrupted.", e);
        }
    }

    @Override
    public long dataSize() { return 0; }

    @Override
    public void finishAndContinue() {
    }

    @Override
    public void finishAndSilent(boolean success) {
        finishMasking();
    }

    @Override
    public ResultBuilder.ResultSetRowCountUpdateBuild newRowCountUpdate() {
        return new WorkspaceNoopResultBuild(this.stream);
    }

    @Override
    public void flushData() {
    }

    @Override
    public void collectCost(long costTimeMs) {
    }

    @Override
    public void finishRecord(boolean success) {
        finishMasking();
    }

    @Override
    public void finishRecord(boolean success, String message, Throwable e) {
        finishMasking();
        if (!success) {
            Throwable err = e != null ? e : new RuntimeException(message == null ? "Remote source row stream failed." : message);
            this.stream.fail(err);
        }
    }

    private void finishMasking() {
        if (this.maskingActive) {
            this.valueProcessService.finish(this.queryRequest, this.maskFlash);
        }
    }
}

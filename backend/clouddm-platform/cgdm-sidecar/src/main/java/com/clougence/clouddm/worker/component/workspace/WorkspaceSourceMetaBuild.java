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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.duckdb.DuckDBColumnType;

import com.clougence.clouddm.api.common.GlobalConfUtils;
import com.clougence.clouddm.base.metadata.ds.ColMetaData;
import com.clougence.clouddm.sdk.execute.session.QueryRequest;
import com.clougence.clouddm.sdk.execute.session.ResultBuilder;
import com.clougence.clouddm.sdk.execute.session.ResultColMeta;
import com.clougence.clouddm.sdk.execute.session.result.ColumnConfig;
import com.clougence.clouddm.sdk.execute.session.result.ReaderOptions;
import com.clougence.clouddm.sdk.execute.session.result.ValueProcessService;

public class WorkspaceSourceMetaBuild implements ResultBuilder.ResultSetMetaBuild {

    private final WorkspaceSourceStream stream;
    private final QueryRequest          queryRequest;
    private final ValueProcessService   valueProcessService;
    private final long                  maxRows;
    private final boolean               metadataOnly;

    public WorkspaceSourceMetaBuild(WorkspaceSourceStream stream, QueryRequest queryRequest, ValueProcessService valueProcessService, long maxRows, boolean metadataOnly){
        this.stream = stream;
        this.queryRequest = queryRequest;
        this.valueProcessService = valueProcessService;
        this.maxRows = maxRows;
        this.metadataOnly = metadataOnly;
    }

    @Override
    public void setRefreshOnProgress(boolean refreshOnProgress) {
    }

    @Override
    public ResultBuilder.ResultSetRowsBuild receiveMeta(Map<String, ResultColMeta> rowMeta) throws SQLException, IOException {
        List<WorkspaceSourceColumn> columns = new ArrayList<>();
        Map<String, ColumnConfig> colConfigs = this.queryRequest == null ? null : this.queryRequest.getColumnList();
        boolean queryMasking = this.queryRequest != null && this.queryRequest.isUsingValueProcess();

        for (Map.Entry<String, ResultColMeta> entry : rowMeta.entrySet()) {
            ResultColMeta colMeta = entry.getValue();
            ColMetaData meta = colMeta.getMeta();
            String colName = meta != null && meta.getColumn() != null ? meta.getColumn() : entry.getKey();
            boolean forceVarchar = queryMasking;
            if (colConfigs != null && colConfigs.containsKey(colName)) {
                forceVarchar = colConfigs.get(colName).isUsingValueProcess();
            }
            DuckDBColumnType duckDbType = WorkspaceTypeMapper.toDuckDbType(meta, colMeta.getFetcher(), forceVarchar);
            columns.add(new WorkspaceSourceColumn(colName, meta, colMeta.getFetcher(), duckDbType, forceVarchar));
        }

        this.stream.completeMeta(columns);

        ReaderOptions options = new ReaderOptions();
        options.setTempPath(GlobalConfUtils.getTempDataHome());
        if (this.queryRequest != null && this.queryRequest.getResultConf() != null) {
            options.setColumnBytesLimit(this.queryRequest.getResultConf().getFetchColumnBytesLimit());
            options.setElementBytesLimit(this.queryRequest.getResultConf().getFetchElementBytesLimit());
            options.setDataFormat(this.queryRequest.getResultConf().getDataFormat());
            options.setTimeFormat(this.queryRequest.getResultConf().getTimeFormat());
            options.setDataTimeFormat(this.queryRequest.getResultConf().getDataTimeFormat());
            options.setTimeWithZoneFormat(this.queryRequest.getResultConf().getTimeWithZoneFormat());
            options.setDataTimeWithZoneFormat(this.queryRequest.getResultConf().getDataTimeWithZoneFormat());
        }
        return new WorkspaceSourceRowsBuild(this.stream, this.queryRequest, this.metadataOnly ? null : this.valueProcessService, this.maxRows, columns, options);
    }

    @Override
    public void collectCost(long costTimeMs) {
    }

    @Override
    public void finishRecord(boolean success) {
    }

    @Override
    public void finishRecord(boolean success, String message, Throwable e) {
        if (!success) {
            Throwable err = e != null ? e : new RuntimeException(message == null ? "Remote source metadata failed." : message);
            this.stream.fail(err);
        }
    }
}

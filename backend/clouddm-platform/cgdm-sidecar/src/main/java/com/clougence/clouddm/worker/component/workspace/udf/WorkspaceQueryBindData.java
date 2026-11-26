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
import java.util.concurrent.atomic.AtomicBoolean;

import org.duckdb.DuckDBTableFunctionState;

import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceSourcePlanDTO;
import com.clougence.clouddm.worker.component.workspace.WorkspaceSourceColumn;
import com.clougence.clouddm.worker.component.workspace.WorkspaceSourceStream;

import lombok.Getter;

@Getter
public class WorkspaceQueryBindData implements DuckDBTableFunctionState {

    private final WorkspaceSourcePlanDTO      plan;
    private final List<WorkspaceSourceColumn> columns;
    private final WorkspaceSourceStream       initialStream;
    private final AtomicBoolean               initialStreamClaimed = new AtomicBoolean(false);

    public WorkspaceQueryBindData(WorkspaceSourcePlanDTO plan, List<WorkspaceSourceColumn> columns, WorkspaceSourceStream initialStream){
        this.plan = plan;
        this.columns = columns;
        this.initialStream = initialStream;
    }

    public WorkspaceSourceStream claimInitialStream() {
        if (this.initialStreamClaimed.compareAndSet(false, true)) {
            return this.initialStream;
        }
        return null;
    }

    @Override
    public void close() {
        if (!this.initialStreamClaimed.get() && this.initialStream != null) {
            this.initialStream.close();
        }
    }
}

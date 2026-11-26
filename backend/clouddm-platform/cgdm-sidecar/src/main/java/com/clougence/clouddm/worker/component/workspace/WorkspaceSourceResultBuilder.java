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

import java.util.concurrent.atomic.AtomicInteger;

import com.clougence.clouddm.sdk.execute.session.QueryRequest;
import com.clougence.clouddm.sdk.execute.session.ResultBuilder;
import com.clougence.clouddm.sdk.execute.session.result.ValueProcessService;

public class WorkspaceSourceResultBuilder implements ResultBuilder {

    private final WorkspaceSourceStream stream;
    private final ValueProcessService   valueProcessService;
    private final long                  maxRows;
    private final boolean               metadataOnly;
    private final AtomicInteger         resultIdx = new AtomicInteger();

    public WorkspaceSourceResultBuilder(WorkspaceSourceStream stream, ValueProcessService valueProcessService, long maxRows, boolean metadataOnly){
        this.stream = stream;
        this.valueProcessService = valueProcessService;
        this.maxRows = maxRows;
        this.metadataOnly = metadataOnly;
    }

    @Override
    public String newResultId(QueryRequest query) {
        return "ws_src_" + this.resultIdx.incrementAndGet();
    }

    @Override
    public PhaseBuild newPhase(QueryRequest query) {
        return new WorkspaceNoopResultBuild(this.stream);
    }

    @Override
    public PhaseBuild newPhase(QueryRequest query, String resultId) {
        return new WorkspaceNoopResultBuild(this.stream);
    }

    @Override
    public ResultSetMetaBuild newResultMeta(QueryRequest query, String resultId) {
        return new WorkspaceSourceMetaBuild(this.stream, query, this.valueProcessService, this.maxRows, this.metadataOnly);
    }

    @Override
    public ResultCountBuild newResultCount(QueryRequest query) {
        return new WorkspaceNoopResultBuild(this.stream);
    }

    @Override
    public ResultOutputBuild newOutput(QueryRequest query) {
        return new WorkspaceNoopResultBuild(this.stream);
    }

    @Override
    public ResultMessageBuild newMessage(QueryRequest query) {
        return new WorkspaceNoopResultBuild(this.stream);
    }

    @Override
    public void finished() {
    }
}

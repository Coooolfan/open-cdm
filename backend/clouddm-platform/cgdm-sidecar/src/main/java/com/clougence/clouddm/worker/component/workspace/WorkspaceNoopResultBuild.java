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

import java.util.Map;

import com.clougence.clouddm.sdk.execute.resultset.echo.ResultPhaseType;
import com.clougence.clouddm.sdk.execute.session.MessageLevel;
import com.clougence.clouddm.sdk.execute.session.QueryArg;
import com.clougence.clouddm.sdk.execute.session.ResultBuilder;

public class WorkspaceNoopResultBuild implements ResultBuilder.PhaseBuild, ResultBuilder.ResultCountBuild, ResultBuilder.ResultOutputBuild, ResultBuilder.ResultMessageBuild,
        ResultBuilder.ResultSetRowCountUpdateBuild {

    private final WorkspaceSourceStream stream;

    public WorkspaceNoopResultBuild(WorkspaceSourceStream stream){
        this.stream = stream;
    }

    @Override
    public void collectCost(long costTimeMs) {
    }

    @Override
    public void finishRecord(boolean success) {
    }

    @Override
    public void finishRecord(boolean success, String message, Throwable e) {
        if (!success && this.stream != null) {
            Throwable err = e != null ? e : new RuntimeException(message == null ? "Remote source query failed." : message);
            this.stream.fail(err);
        }
    }

    @Override
    public void onPhase(ResultPhaseType type) {
    }

    @Override
    public void receiveUpdateCount(long updateCount) {
    }

    @Override
    public void receiveGeneratedKey(Map<String, String> generatedData) {
    }

    @Override
    public void receiveOutParam(QueryArg paramDef, String value) {
    }

    @Override
    public void receiveMessage(MessageLevel level, String message) {
        receiveMessage(level, message, true);
    }

    @Override
    public void receiveMessage(MessageLevel level, String message, boolean notify) {
        if (level == MessageLevel.Error && this.stream != null && message != null) {
            this.stream.fail(new RuntimeException(message));
        }
    }

    @Override
    public void collectMetric(int fetchCount) {
    }
}

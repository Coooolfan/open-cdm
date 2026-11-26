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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceSourcePlanDTO;

public class WorkspaceExecutionContext {

    private final Map<String, WorkspaceSourcePlanDTO> sourcePlans   = new ConcurrentHashMap<>();
    private final Set<WorkspaceSourceStream>          activeStreams = ConcurrentHashMap.newKeySet();

    public void loadPlans(Map<String, WorkspaceSourcePlanDTO> plans) {
        this.sourcePlans.clear();
        if (plans != null) {
            this.sourcePlans.putAll(plans);
        }
    }

    public WorkspaceSourcePlanDTO getPlan(String refId) {
        WorkspaceSourcePlanDTO plan = this.sourcePlans.get(refId);
        if (plan == null) {
            throw new IllegalArgumentException("Unauthorized or unknown workspace source refId: " + refId);
        }
        return plan;
    }

    public void registerStream(WorkspaceSourceStream stream) {
        this.activeStreams.add(stream);
    }

    public void unregisterStream(WorkspaceSourceStream stream) {
        this.activeStreams.remove(stream);
    }

    public void cancelAllStreams() {
        for (WorkspaceSourceStream stream : this.activeStreams) {
            stream.cancel();
        }
    }

    public void clear() {
        cancelAllStreams();
        this.activeStreams.clear();
        this.sourcePlans.clear();
    }
}

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
package com.clougence.clouddm.console.web.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.clougence.clouddm.platform.dal.model.system.DmSysWorkerDO;

public final class WorkerRouteUtils {

    private WorkerRouteUtils(){
    }

    /**
     * For regular queries and jobs, prefer pure NORMAL workers (workspaceEnabled != true)
     * when available so heavy DuckDB workspace workloads do not affect online traffic.
     */
    public static List<DmSysWorkerDO> orderForNormalTraffic(List<DmSysWorkerDO> connectedWorkers) {
        if (connectedWorkers == null || connectedWorkers.size() <= 1) {
            return connectedWorkers;
        }
        List<DmSysWorkerDO> normalOnly = new ArrayList<>();
        List<DmSysWorkerDO> workspaceEnabled = new ArrayList<>();
        for (DmSysWorkerDO worker : connectedWorkers) {
            if (Boolean.TRUE.equals(worker.getWorkspaceEnabled())) {
                workspaceEnabled.add(worker);
            } else {
                normalOnly.add(worker);
            }
        }
        if (normalOnly.isEmpty() || workspaceEnabled.isEmpty()) {
            return connectedWorkers;
        }
        List<DmSysWorkerDO> ordered = new ArrayList<>(connectedWorkers.size());
        ordered.addAll(normalOnly);
        ordered.addAll(workspaceEnabled);
        return ordered;
    }

    public static DmSysWorkerDO pickNormalWorker(List<DmSysWorkerDO> connectedWorkers) {
        if (connectedWorkers == null || connectedWorkers.isEmpty()) {
            return null;
        }
        List<DmSysWorkerDO> normalOnly = new ArrayList<>();
        for (DmSysWorkerDO worker : connectedWorkers) {
            if (!Boolean.TRUE.equals(worker.getWorkspaceEnabled())) {
                normalOnly.add(worker);
            }
        }
        List<DmSysWorkerDO> candidates = normalOnly.isEmpty() ? connectedWorkers : normalOnly;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    public static DmSysWorkerDO pickWorkspaceWorker(List<DmSysWorkerDO> connectedWorkers) {
        if (connectedWorkers == null || connectedWorkers.isEmpty()) {
            return null;
        }
        List<DmSysWorkerDO> workspaceWorkers = new ArrayList<>();
        for (DmSysWorkerDO worker : connectedWorkers) {
            if (Boolean.TRUE.equals(worker.getWorkspaceEnabled())) {
                workspaceWorkers.add(worker);
            }
        }
        if (workspaceWorkers.isEmpty()) {
            return null;
        }
        return workspaceWorkers.get(ThreadLocalRandom.current().nextInt(workspaceWorkers.size()));
    }
}

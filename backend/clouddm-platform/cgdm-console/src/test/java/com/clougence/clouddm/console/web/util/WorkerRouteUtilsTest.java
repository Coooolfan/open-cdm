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

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.clougence.clouddm.platform.dal.model.system.DmSysWorkerDO;

public class WorkerRouteUtilsTest {

    @Test
    public void testNormalTrafficPrefersNonWorkspaceWorkers() {
        DmSysWorkerDO normal = worker("w-normal", false);
        DmSysWorkerDO workspace = worker("w-workspace", true);

        for (int i = 0; i < 20; i++) {
            DmSysWorkerDO picked = WorkerRouteUtils.pickNormalWorker(List.of(workspace, normal));
            Assert.assertEquals("w-normal", picked.getWorkerSeqNumber());
        }

        List<DmSysWorkerDO> ordered = WorkerRouteUtils.orderForNormalTraffic(List.of(workspace, normal));
        Assert.assertEquals("w-normal", ordered.get(0).getWorkerSeqNumber());
        Assert.assertEquals("w-workspace", ordered.get(1).getWorkerSeqNumber());
    }

    @Test
    public void testNormalTrafficFallsBackWhenOnlyWorkspaceWorkersExist() {
        DmSysWorkerDO alone = worker("w-alone", true);
        DmSysWorkerDO picked = WorkerRouteUtils.pickNormalWorker(List.of(alone));
        Assert.assertEquals("w-alone", picked.getWorkerSeqNumber());
    }

    @Test
    public void testWorkspaceTrafficOnlyPicksWorkspaceWorkers() {
        DmSysWorkerDO normal = worker("w-normal", false);
        DmSysWorkerDO workspace = worker("w-workspace", true);

        Assert.assertNull(WorkerRouteUtils.pickWorkspaceWorker(List.of(normal)));
        Assert.assertEquals("w-workspace", WorkerRouteUtils.pickWorkspaceWorker(List.of(normal, workspace)).getWorkerSeqNumber());
    }

    private static DmSysWorkerDO worker(String wsn, boolean workspaceEnabled) {
        DmSysWorkerDO w = new DmSysWorkerDO();
        w.setWorkerSeqNumber(wsn);
        w.setWorkspaceEnabled(workspaceEnabled);
        return w;
    }
}

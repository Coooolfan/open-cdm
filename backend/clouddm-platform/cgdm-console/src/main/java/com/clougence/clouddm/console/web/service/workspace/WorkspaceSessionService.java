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
package com.clougence.clouddm.console.web.service.workspace;

import java.util.List;

import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceQueryPlanDTO;
import com.clougence.clouddm.console.web.model.vo.workspace.WorkspaceClusterVO;
import com.clougence.clouddm.console.web.model.vo.workspace.WorkspaceDataSourceVO;
import com.clougence.clouddm.platform.dal.model.execution.DmExecSessionDO;

public interface WorkspaceSessionService {

    List<WorkspaceClusterVO> listClusters(String puid, String uid);

    List<WorkspaceDataSourceVO> listClusterDataSources(String puid, String uid, long clusterId);

    String createSession(String puid, String uid, long clusterId, String requestedSessionId);

    DmExecSessionDO ensureActiveSession(String puid, String uid, String sessionId, long clusterId);

    void closeSession(String puid, String uid, String sessionId);

    void asyncExecuteQuery(DmExecSessionDO session, String batchId, List<WorkspaceQueryPlanDTO> plans);
}

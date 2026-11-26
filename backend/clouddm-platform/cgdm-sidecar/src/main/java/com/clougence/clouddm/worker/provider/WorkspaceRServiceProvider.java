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
package com.clougence.clouddm.worker.provider;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.clougence.clouddm.api.common.boot.UnifiedPostConstruct;
import com.clougence.clouddm.api.sidecar.session.execute.AsyncWaitResult;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceQueryPlanDTO;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceRService;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceSessionInitDTO;
import com.clougence.clouddm.comm.RSocketApiClass;
import com.clougence.clouddm.comm.model.RSocketSendDTO;
import com.clougence.clouddm.sdk.execute.session.QueryRequest;
import com.clougence.clouddm.sdk.execute.session.QueryResultConf;
import com.clougence.clouddm.worker.component.resource.OnlineDsResourceManager;
import com.clougence.clouddm.worker.component.session.SessionAgent;
import com.clougence.clouddm.worker.component.session.SessionManager;
import com.clougence.clouddm.worker.component.workspace.WorkspaceQueryRequest;
import com.clougence.utils.ExceptionUtils;
import com.clougence.utils.StringUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RSocketApiClass
public class WorkspaceRServiceProvider implements WorkspaceRService, UnifiedPostConstruct {

    @Resource
    private OnlineDsResourceManager onlineRM;
    @Resource
    private SessionManager          sessionManager;

    @Override
    public void init() throws Exception {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean createWorkspaceSession(RSocketSendDTO sendDTO, WorkspaceSessionInitDTO initDTO) {
        if (initDTO == null || StringUtils.isBlank(initDTO.getSessionId())) {
            throw new IllegalArgumentException("workspace sessionId is blank.");
        }
        try {
            log.info("receive create workspace session request, sessionId={}, clusterId={}", initDTO.getSessionId(), initDTO.getClusterId());
            SessionAgent agent = this.sessionManager.createWorkspaceSession(this.onlineRM, initDTO);
            return agent != null;
        } catch (Exception e) {
            String msg = "createWorkspaceSession error. msg: " + ExceptionUtils.getRootCauseMessage(e);
            log.error(msg, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public AsyncWaitResult asyncExecuteWorkspaceQuery(RSocketSendDTO sendDTO, String sessionId, String batchId, List<WorkspaceQueryPlanDTO> plans) {
        if (!this.sessionManager.hasSessionById(sessionId)) {
            throw new IllegalStateException("workspace sessionId '" + sessionId + "' does not exist.");
        }
        SessionAgent agent = this.sessionManager.getSessionById(sessionId);
        List<QueryRequest> requests = new ArrayList<>();
        if (plans != null) {
            for (WorkspaceQueryPlanDTO plan : plans) {
                WorkspaceQueryRequest req = new WorkspaceQueryRequest();
                req.setIndex(plan.getIndex());
                req.setBatchId(batchId);
                req.setQueryId(plan.getQueryId());
                req.setQueryBody(plan.getOriginalSql());
                req.setOriginalBody(plan.getOriginalSql());
                req.setUseExplain(plan.isUseExplain());
                req.setUsingValueProcess(false);
                req.setResultConf(plan.getResultConf() != null ? plan.getResultConf() : new QueryResultConf());
                req.setWorkspacePlan(plan);
                requests.add(req);
            }
        }
        return agent.submitQueries(batchId, requests);
    }
}

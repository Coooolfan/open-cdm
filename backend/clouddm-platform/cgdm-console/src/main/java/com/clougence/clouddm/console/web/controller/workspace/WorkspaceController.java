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
package com.clougence.clouddm.console.web.controller.workspace;

import static com.clougence.clouddm.sdk.security.auth.def.SecRoleAuthLabel.DM_QUERY_CONSOLE;

import java.util.List;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.clougence.clouddm.api.common.rpc.ResWebData;
import com.clougence.clouddm.api.common.rpc.ResWebDataUtils;
import com.clougence.clouddm.console.web.constants.DmControllerUrlPrefix;
import com.clougence.clouddm.console.web.global.jwtsession.RequestAuth;
import com.clougence.clouddm.console.web.model.fo.editor.query.CloseSessionFO;
import com.clougence.clouddm.console.web.model.fo.workspace.CreateWorkspaceSessionFO;
import com.clougence.clouddm.console.web.model.fo.workspace.ListWorkspaceDsFO;
import com.clougence.clouddm.console.web.model.vo.editor.query.OperationSessionVO;
import com.clougence.clouddm.console.web.model.vo.workspace.WorkspaceClusterVO;
import com.clougence.clouddm.console.web.model.vo.workspace.WorkspaceDataSourceVO;
import com.clougence.clouddm.console.web.service.auth.RdpUserService;
import com.clougence.clouddm.console.web.service.workspace.WorkspaceSessionService;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(value = DmControllerUrlPrefix.CONSOLE_PREFIX + "/workspace")
public class WorkspaceController {

    @Resource
    private WorkspaceSessionService workspaceSessionService;

    @RequestAuth(DM_QUERY_CONSOLE)
    @RequestMapping(value = "/listClusters", method = RequestMethod.POST)
    public ResWebData<List<WorkspaceClusterVO>> listClusters(HttpServletRequest request) {
        String puid = (String) request.getAttribute(RdpUserService.PUID);
        String uid = (String) request.getAttribute(RdpUserService.UID);
        return ResWebDataUtils.buildSuccess(this.workspaceSessionService.listClusters(puid, uid));
    }

    @RequestAuth(DM_QUERY_CONSOLE)
    @RequestMapping(value = "/listClusterDataSources", method = RequestMethod.POST)
    public ResWebData<List<WorkspaceDataSourceVO>> listClusterDataSources(@Valid @RequestBody ListWorkspaceDsFO fo, HttpServletRequest request) {
        String puid = (String) request.getAttribute(RdpUserService.PUID);
        String uid = (String) request.getAttribute(RdpUserService.UID);
        return ResWebDataUtils.buildSuccess(this.workspaceSessionService.listClusterDataSources(puid, uid, fo.getClusterId()));
    }

    @RequestAuth(DM_QUERY_CONSOLE)
    @RequestMapping(value = "/createSession", method = RequestMethod.POST)
    public ResWebData<OperationSessionVO> createSession(@Valid @RequestBody CreateWorkspaceSessionFO fo, HttpServletRequest request) {
        String puid = (String) request.getAttribute(RdpUserService.PUID);
        String uid = (String) request.getAttribute(RdpUserService.UID);
        String sessionId = this.workspaceSessionService.createSession(puid, uid, fo.getClusterId(), fo.getSessionId());
        return ResWebDataUtils.buildSuccess(new OperationSessionVO(sessionId, false));
    }

    @RequestAuth(DM_QUERY_CONSOLE)
    @RequestMapping(value = "/closeSession", method = RequestMethod.POST)
    public ResWebData<List<String>> closeSession(@Valid @RequestBody CloseSessionFO fo, HttpServletRequest request) {
        String puid = (String) request.getAttribute(RdpUserService.PUID);
        String uid = (String) request.getAttribute(RdpUserService.UID);
        if (fo.getSessionId() != null) {
            for (String sessionId : fo.getSessionId()) {
                this.workspaceSessionService.closeSession(puid, uid, sessionId);
            }
        }
        return ResWebDataUtils.buildSuccess(fo.getSessionId());
    }
}

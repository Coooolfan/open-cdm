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

import java.util.*;

import org.springframework.stereotype.Service;

import com.clougence.clouddm.api.common.exception.ErrorMessageException;
import com.clougence.clouddm.api.sidecar.session.execute.AsyncWaitResult;
import com.clougence.clouddm.api.sidecar.session.execute.ExecuteRService;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceQueryPlanDTO;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceRService;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceSessionInitDTO;
import com.clougence.clouddm.comm.model.RSocketSendDTO;
import com.clougence.clouddm.comm.model.RSocketSendType;
import com.clougence.clouddm.console.web.component.dsconfig.DmDsConfigService;
import com.clougence.clouddm.console.web.component.execute.QueryService;
import com.clougence.clouddm.console.web.global.i18n.DmI18nUtils;
import com.clougence.clouddm.console.web.global.i18n.I18nDmMsgKeys;
import com.clougence.clouddm.console.web.model.vo.cluster.ClusterVO;
import com.clougence.clouddm.console.web.model.vo.workspace.WorkspaceClusterVO;
import com.clougence.clouddm.console.web.model.vo.workspace.WorkspaceDataSourceVO;
import com.clougence.clouddm.console.web.service.cluster.ClusterService;
import com.clougence.clouddm.console.web.util.WorkerRouteUtils;
import com.clougence.clouddm.platform.dal.access.DataSourceDal;
import com.clougence.clouddm.platform.dal.access.ExecutionDal;
import com.clougence.clouddm.platform.dal.access.SystemDal;
import com.clougence.clouddm.platform.dal.model.datasource.DmDsDO;
import com.clougence.clouddm.platform.dal.model.execution.DmExecSessionDO;
import com.clougence.clouddm.platform.dal.model.execution.DsSessionType;
import com.clougence.clouddm.platform.dal.model.system.DmSysWorkerDO;
import com.clougence.clouddm.sdk.execute.dsconf.DsConfigField;
import com.clougence.clouddm.sdk.execute.session.SessionContextDTO;
import com.clougence.utils.JsonUtils;
import com.clougence.utils.StringUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WorkspaceSessionServiceImpl implements WorkspaceSessionService {

    private static final int  DEFAULT_WORKSPACE_IDLE_SEC = 1800;

    @Resource
    private SystemDal         systemDal;
    @Resource
    private ExecutionDal      execDal;
    @Resource
    private DataSourceDal     dsDal;
    @Resource
    private ClusterService    clusterService;
    @Resource
    private DmDsConfigService dmDsConfigService;
    @Resource
    private QueryService      queryService;
    @Resource
    private ExecuteRService   executeRService;
    @Resource
    private WorkspaceRService workspaceRService;

    @Override
    public List<WorkspaceClusterVO> listClusters(String puid, String uid) {
        List<ClusterVO> clusters = this.clusterService.listByOwnerUid(puid);
        if (clusters == null || clusters.isEmpty()) {
            return Collections.emptyList();
        }
        List<WorkspaceClusterVO> result = new ArrayList<>(clusters.size());
        for (ClusterVO cluster : clusters) {
            List<DmSysWorkerDO> connected = this.systemDal.workerMapper().queryConnectedByClusterId(cluster.getId());
            int workspaceCount = 0;
            for (DmSysWorkerDO worker : connected) {
                if (Boolean.TRUE.equals(worker.getWorkspaceEnabled())) {
                    workspaceCount++;
                }
            }

            WorkspaceClusterVO vo = new WorkspaceClusterVO();
            vo.setClusterId(cluster.getId());
            vo.setClusterName(cluster.getClusterName());
            vo.setClusterDesc(cluster.getClusterDesc());
            vo.setRegion(cluster.getRegion());
            vo.setConnectedWorkerCount(connected.size());
            vo.setWorkspaceWorkerCount(workspaceCount);
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<WorkspaceDataSourceVO> listClusterDataSources(String puid, String uid, long clusterId) {
        List<DmDsDO> ownerDsList = this.dsDal.dsMapper().listByOwner(puid);
        if (ownerDsList == null || ownerDsList.isEmpty()) {
            return Collections.emptyList();
        }

        List<WorkspaceDataSourceVO> result = new ArrayList<>();
        for (DmDsDO ds : ownerDsList) {
            if (!Objects.equals(ds.getBindClusterId(), clusterId)) {
                continue;
            }
            String displayName = StringUtils.isNotBlank(ds.getInstanceDesc()) ? ds.getInstanceDesc() : ds.getInstanceId();
            WorkspaceDataSourceVO vo = new WorkspaceDataSourceVO();
            vo.setDsId(ds.getId());
            vo.setDsEnvId(ds.getDsEnvId());
            vo.setInstanceId(ds.getInstanceId());
            vo.setInstanceDesc(ds.getInstanceDesc());
            vo.setDataSourceType(ds.getDataSourceType());
            vo.setReferenceToken(formatReferenceToken(displayName));
            vo.setDefaultDatabase(this.dmDsConfigService.fetchDsConfig(ds.getId(), DsConfigField.DEFAULT_DATABASE.getConfigName()));
            vo.setDefaultSchema(this.dmDsConfigService.fetchDsConfig(ds.getId(), DsConfigField.DEFAULT_SCHEMA.getConfigName()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public String createSession(String puid, String uid, long clusterId, String requestedSessionId) {
        String sessionId = StringUtils.isNotBlank(requestedSessionId) ? requestedSessionId : "ws_" + UUID.randomUUID().toString().replace("-", "");

        DmExecSessionDO existing = this.execDal.sessionMapper().queryBySessionId(uid, sessionId);
        if (existing != null) {
            this.queryService.closeSession(uid, sessionId);
        }

        List<DmSysWorkerDO> workspaceWorkers = this.systemDal.workerMapper().queryConnectedWorkspaceByClusterId(clusterId);
        DmSysWorkerDO targetWorker = WorkerRouteUtils.pickWorkspaceWorker(workspaceWorkers);
        if (targetWorker == null) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_NO_AVAILABLE_WORKER_ERROR.name()));
        }

        RSocketSendDTO sendDTO = buildSendDTO(targetWorker);
        SessionContextDTO ctxDTO = new SessionContextDTO();
        ctxDTO.setSessionId(sessionId);
        ctxDTO.setMaxIdleTimeSec(DEFAULT_WORKSPACE_IDLE_SEC);
        ctxDTO.setRdbAutoCommit(true);
        ctxDTO.setRdbReadOnly(false);

        DmExecSessionDO sessionDO = new DmExecSessionDO();
        sessionDO.setUid(uid);
        sessionDO.setSessionId(sessionId);
        sessionDO.setSessionType(DsSessionType.WORKSPACE);
        sessionDO.setWsn(targetWorker.getWorkerSeqNumber());
        sessionDO.setClusterId(String.valueOf(clusterId));
        sessionDO.setDatasourceId(null);
        sessionDO.setDatasourceType("WORKSPACE");
        sessionDO.setConfig(JsonUtils.toJson(ctxDTO));
        sessionDO.setGmtCreate(new Date());
        sessionDO.setGmtModified(new Date());

        int insert = this.execDal.sessionMapper().insert(sessionDO);
        if (insert != 1) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.CONSOLE_QUERY_STORE_SESSION_DATA_ERROR.name()));
        }

        WorkspaceSessionInitDTO initDTO = new WorkspaceSessionInitDTO();
        initDTO.setSessionId(sessionId);
        initDTO.setClusterId(clusterId);
        initDTO.setMaxIdleTimeSec(DEFAULT_WORKSPACE_IDLE_SEC);
        try {
            if (!this.workspaceRService.createWorkspaceSession(sendDTO, initDTO)) {
                throw new IllegalStateException("Create workspace session on sidecar returned false.");
            }
        } catch (Exception e) {
            this.execDal.sessionMapper().deleteBySessionId(sessionId);
            throw e;
        }
        return sessionId;
    }

    @Override
    public DmExecSessionDO ensureActiveSession(String puid, String uid, String sessionId, long clusterId) {
        DmExecSessionDO session = this.execDal.sessionMapper().queryBySessionId(uid, sessionId);
        if (session != null) {
            DmSysWorkerDO worker = this.systemDal.workerMapper().queryConnectedByWsn(session.getWsn());
            if (worker != null && Boolean.TRUE.equals(worker.getWorkspaceEnabled())) {
                RSocketSendDTO sendDTO = buildSendDTO(worker);
                if (this.executeRService.hasSession(sendDTO, sessionId)) {
                    return session;
                }
            }
        }
        createSession(puid, uid, clusterId, sessionId);
        return this.execDal.sessionMapper().queryBySessionId(uid, sessionId);
    }

    @Override
    public void closeSession(String puid, String uid, String sessionId) {
        this.queryService.closeSession(uid, sessionId);
    }

    @Override
    public void asyncExecuteQuery(DmExecSessionDO session, String batchId, List<WorkspaceQueryPlanDTO> plans) {
        DmSysWorkerDO worker = this.systemDal.workerMapper().queryConnectedByWsn(session.getWsn());
        if (worker == null) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.CONSOLE_QUERY_WORKER_STATUS_OFFLINE_ERROR.name(), session.getWsn()));
        }
        RSocketSendDTO sendDTO = buildSendDTO(worker);
        AsyncWaitResult res = this.workspaceRService.asyncExecuteWorkspaceQuery(sendDTO, session.getSessionId(), batchId, plans);
        if (res != null && !res.isSuccess()) {
            throw new ErrorMessageException(res.getMessage());
        }
    }

    private static RSocketSendDTO buildSendDTO(DmSysWorkerDO worker) {
        RSocketSendDTO sendDTO = new RSocketSendDTO();
        sendDTO.setClusterId(worker.getClusterId());
        sendDTO.setWorkerSeqNumber(worker.getWorkerSeqNumber());
        sendDTO.setWorkerIP(worker.getWorkerIp());
        sendDTO.setUid(worker.getUid());
        sendDTO.setRSocketSendType(RSocketSendType.SPECIFIED);
        return sendDTO;
    }

    private static String formatReferenceToken(String name) {
        if (StringUtils.isBlank(name)) {
            return "#\"\"";
        }
        boolean simple = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c == '_' || c == '-' || Character.isLetterOrDigit(c))) {
                simple = false;
                break;
            }
        }
        if (simple) {
            return "#" + name;
        }
        return "#\"" + name.replace("\"", "\"\"") + "\"";
    }
}

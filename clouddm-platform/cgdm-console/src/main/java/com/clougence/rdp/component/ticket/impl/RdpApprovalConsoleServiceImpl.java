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
package com.clougence.rdp.component.ticket.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.clougence.clouddm.platform.plugin.PluginManager;
import com.clougence.clouddm.sdk.approval.ApprovalProviderSpi;
import com.clougence.clouddm.sdk.approval.UserDetail;
import com.clougence.clouddm.sdk.service.approval.RdpApprovalActivityInfo;
import com.clougence.clouddm.sdk.service.approval.RdpApprovalActivityStatus;
import com.clougence.clouddm.sdk.service.approval.RdpApprovalConsoleService;
import com.clougence.clouddm.sdk.service.approval.RdpApprovalTicketInfo;
import com.clougence.rdp.component.ticket.RdpApprovalService;
import com.clougence.clouddm.console.web.dal.enumeration.RdpTicketStage;
import com.clougence.clouddm.console.web.dal.mapper.RdpTicketMapper;
import com.clougence.clouddm.console.web.dal.mapper.RdpTicketProcessActivityMapper;
import com.clougence.clouddm.console.web.dal.mapper.RdpTicketProcessMapper;
import com.clougence.clouddm.console.web.dal.model.RdpTicketDO;
import com.clougence.clouddm.console.web.dal.model.RdpTicketProcessActivityDO;
import com.clougence.clouddm.console.web.dal.model.RdpTicketProcessDO;
import com.clougence.utils.JsonUtils;
import com.clougence.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RdpApprovalConsoleServiceImpl implements RdpApprovalConsoleService {

    @Resource
    private RdpTicketMapper                rdpTicketMapper;
    @Resource
    private RdpTicketProcessMapper         rdpTicketProcessMapper;
    @Resource
    private RdpTicketProcessActivityMapper activityMapper;
    @Resource
    private RdpApprovalService             rdpApproService;

    @Override
    @SneakyThrows
    public void refreshTicket(RdpApprovalTicketInfo callback) {
        RdpTicketDO rdpTicketDO = rdpTicketMapper.queryByApproIdentity(callback.getApproIdentity(), callback.getProviderType(), callback.getOwnerUid());
        if (rdpTicketDO == null) {
            log.error("Callback event not find ticket for approval instance: {} and type: {} and puid: {}", callback.getApproIdentity(), callback.getProviderType(), callback
                .getOwnerUid());
            return;
        }
        rdpApproService.refreshApprovalStatus(rdpTicketDO.getId());
    }

    @Override
    @SneakyThrows
    @Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRED)
    public void updateActivity(RdpApprovalActivityInfo activity) {
        RdpTicketDO rdpTicketDO = rdpTicketMapper.queryByApproIdentity(activity.getApprovalIdentity(), activity.getPlatform(), activity.getPuid());
        // avoid receive another callback
        if (rdpTicketDO == null) {
            log.error("Callback event not find ticket for approval instance: {} and type: {} and puid: {}", activity.getApprovalIdentity(), activity.getPlatform(), activity
                .getPuid());
            return;
        }
        RdpTicketProcessDO processDO = this.rdpTicketProcessMapper.queryByStage(rdpTicketDO.getId(), RdpTicketStage.APPROVAL);
        RdpTicketProcessActivityDO activityDO = activityMapper.queryByProcessIdAndActivityIdForUpdate(processDO.getId(), activity.getActivityId());

        String context = activityDO.getContext();
        List<RdpApprovalActivityInfo> list;
        if (StringUtils.isEmpty(context)) {
            list = new ArrayList<>();
        } else {
            list = JsonUtils.toList(context, new TypeReference<List<RdpApprovalActivityInfo>>() {});
        }

        RdpApprovalActivityInfo originTask = null;
        for (RdpApprovalActivityInfo approvalTask : list) {
            if (approvalTask.getTaskId().equals(activity.getTaskId())) {
                originTask = approvalTask;
                break;
            }
        }
        if (originTask == null && activity.getStatus() != RdpApprovalActivityStatus.CLOSE) {
            if (StringUtils.isEmpty(activity.getUserName())) {
                ApprovalProviderSpi service = PluginManager.findSpi(ApprovalProviderSpi.class, rdpTicketDO.getApproType().name());
                UserDetail userDetail = service.getUserDetailByUid(rdpTicketDO.getPrimaryUid(), activity.getUserId());
                activity.setUserName(userDetail.getUsername());
            }
            list.add(activity);
        } else if (RdpApprovalActivityStatus.canUpdate(originTask.getStatus(), activity.getStatus())) {
            if (activity.getStatus() == RdpApprovalActivityStatus.CLOSE) {
                list.remove(originTask);
            } else {
                originTask.setStatus(activity.getStatus());
                originTask.setRemark(activity.getRemark());
                originTask.setFinishTime(activity.getFinishTime());
            }
        }

        String json = JsonUtils.toJson(list);

        activityMapper.updateContext(processDO.getId(), activityDO.getActivityId(), json);

    }
}

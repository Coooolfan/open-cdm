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
package com.clougence.clouddm.console.web.global.notify;

import org.springframework.stereotype.Service;

import com.clougence.clouddm.console.web.component.project.ImSenderService;
import com.clougence.rdp.component.ticket.RdpTicketLifeCycle;
import com.clougence.clouddm.console.web.dal.enumeration.RdpApprovalBiz;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * @author mode 2020/11/7 17:11
 */
@Slf4j
@Service
public class DmTicketLifeCycleNotify implements RdpTicketLifeCycle {

    @Resource
    protected ImSenderService imSenderService;

    private boolean checkIgnore(RdpApprovalBiz approvalBiz) {
        return !(approvalBiz == RdpApprovalBiz.DATA_SOURCE_AUTH || approvalBiz == RdpApprovalBiz.DM_QUERY);
    }

    @Override
    public void approvalCompleted(RdpApprovalBiz approvalBiz, long ticketId) {
        if (checkIgnore(approvalBiz)) {
        }

        // imSenderService.sendMessage();
        //System.out.println("approvalCompleted ticketId:" + ticketId + " approvalBiz:" + approvalBiz);
    }

    @Override
    public void approvalRefuse(RdpApprovalBiz approvalBiz, long ticketId) {
        if (checkIgnore(approvalBiz)) {
        }

        //System.out.println("approvalRefuse ticketId:" + ticketId + " approvalBiz:" + approvalBiz);
    }

    @Override
    public void approvalFailed(RdpApprovalBiz approvalBiz, long ticketId) {
        if (checkIgnore(approvalBiz)) {
        }

        //System.out.println("approvalFailed ticketId:" + ticketId + " approvalBiz:" + approvalBiz);
    }

    @Override
    public void approvalCanceled(RdpApprovalBiz approvalBiz, long ticketId) {
        if (checkIgnore(approvalBiz)) {
        }

        //System.out.println("approvalCanceled ticketId:" + ticketId + " approvalBiz:" + approvalBiz);
    }
}

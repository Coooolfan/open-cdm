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
package com.clougence.rdp.component.ticket;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.clougence.clouddm.console.web.model.fo.security.ListMyAuthTicketFO;
import com.clougence.clouddm.console.web.model.fo.ticket.RdpApprovalTicketFO;
import com.clougence.clouddm.console.web.model.fo.ticket.RdpListTicketFO;
import com.clougence.clouddm.console.web.model.fo.ticket.RdpQueryTicketDetailFO;
import com.clougence.clouddm.console.web.model.fo.ticket.RdpTicketBasicVO;
import com.clougence.clouddm.console.web.model.vo.ticket.RdpTicketBaseInfoVO;

/**
 * @author Ekko
 * @date 2024/5/8 12:01
*/
public interface RdpTicketService {

    void closeTicket(long ticketId, String statusMessage, String puid, String uid);

    void closeTicket(long ticketId, String statusMessage, String puid);

    void failTicket(long ticketId, String statusMessage, String puid);

    void cancelTicket(String puid, long ticketId, String statusMessage);

    void retryTicket(String puid, long ticketId);

    IPage<RdpTicketBasicVO> queryTicketListByPage(String puid, RdpListTicketFO fo);

    IPage<RdpTicketBasicVO> queryAuthTicketListByPage(String puid, ListMyAuthTicketFO fo);

    RdpTicketBaseInfoVO queryTicketBaseInfo(String puid, String uid, RdpQueryTicketDetailFO fo);

    void approvalTicket(String puid, String uid, RdpApprovalTicketFO fo);

    boolean isFinish(long ticketId);
}

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
package com.clougence.clouddm.console.web.dal.mapper;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.clougence.clouddm.console.web.dal.enumeration.*;
import com.clougence.clouddm.console.web.dal.model.DmProjectChangeDO;
import com.clougence.clouddm.console.web.dal.model.DmProjectChangeFlowWalked;
import com.clougence.clouddm.console.web.dal.model.queryobj.DmChangeQueryObj;

/**
 * @author mode create time is 2020/3/2
 **/
public interface DmProjectChangeMapper extends BaseMapper<DmProjectChangeDO> {

    IPage<DmProjectChangeDO> listChangeByConditionAndPage(Page<?> page, DmChangeQueryObj param);

    DmProjectChangeDO queryChangeById(String ownerUid, long changeId);

    int countUnEndChangeByDevopsId(String ownerUid, long devopsId);

    int countUnEndChangeByProjectId(String ownerUid, long projectId);

    List<DmProjectChangeDO> queryReadyChangeListByDate(Date date, int limit);

    int assignReadyChange(long changeId, int version);

    int increTryTimes(long changeId, int version, String remark);

    int updateStatusTo(long changeId, int version, ProjectChangeStatus toStatus, String remark);

    int updateStepTo(long changeId, int version, ProjectChangeStep toStep, String remark);

    int lockChangeById(long changeId, int version);

    List<DmProjectChangeDO> queryUnLockChange(long refProjectId, long refDevopsId);

    int updateFlowWalked(long changeId, DmProjectChangeFlowWalked flowWalked);

    default int updateFlowWalkedAppend(long changeId, DmProjectChangeDO change, DmChangeCheckStrategy option) {
        DmProjectChangeFlowWalked flowWalked = change.getFlowWalked();
        if (flowWalked == null) {
            flowWalked = new DmProjectChangeFlowWalked();
        }
        flowWalked.setFlowCheck(option);

        return this.updateFlowWalked(changeId, flowWalked);
    }

    default int updateFlowWalkedAppend(long changeId, DmProjectChangeDO change, DmChangeApproveStrategy option) {
        DmProjectChangeFlowWalked flowWalked = change.getFlowWalked();
        if (flowWalked == null) {
            flowWalked = new DmProjectChangeFlowWalked();
        }
        flowWalked.setFlowApprove(option);

        return this.updateFlowWalked(changeId, flowWalked);
    }

    default int updateFlowWalkedAppend(long changeId, DmProjectChangeDO change, DmChangeExecStrategy option) {
        DmProjectChangeFlowWalked flowWalked = change.getFlowWalked();
        if (flowWalked == null) {
            flowWalked = new DmProjectChangeFlowWalked();
        }
        flowWalked.setFlowExecute(option);

        return this.updateFlowWalked(changeId, flowWalked);
    }
}

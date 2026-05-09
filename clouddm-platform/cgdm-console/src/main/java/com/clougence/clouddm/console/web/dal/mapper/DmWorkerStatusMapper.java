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

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clougence.clouddm.comm.constants.worker.WorkerConnStatus;
import com.clougence.clouddm.console.web.dal.model.DmWorkerStatusDO;

/**
 * @author wanshao create time is 2020/7/11
 **/
public interface DmWorkerStatusMapper extends BaseMapper<DmWorkerStatusDO> {

    DmWorkerStatusDO queryByWsn(String workerSeqNumber);

    DmWorkerStatusDO queryOnlineByWsn(String workerSeqNumber);

    List<DmWorkerStatusDO> queryByClusterIdAndStatus(Long clusterId, WorkerConnStatus workerConnStatus);

    /**
     * @deprecated Legacy query from the old consoleIp-based routing model. Avoid adding new usage.
     */
    @Deprecated
    List<DmWorkerStatusDO> queryByConsoleIpAndStatus(String consoleIp, WorkerConnStatus workerConnStatus);

    List<DmWorkerStatusDO> queryInactivity(@Param("checkPoint") Date checkPoint);

    int updateConnInfoByWsn(DmWorkerStatusDO workerStatusDO);

    int updateStatusById(long id, WorkerConnStatus workerConnStatus);

    int deleteByWsn(String workerSeqNumber);
}

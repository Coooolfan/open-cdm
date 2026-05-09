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

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.clougence.clouddm.console.web.dal.enumeration.WorkerHeartbeatType;
import com.clougence.clouddm.console.web.dal.model.DmWorkerHeartbeatDO;

/**
 * @author bucketli 2020-01-22 15:20
 * @since 1.1.3
 */
public interface DmWorkerHeartbeatMapper extends BaseMapper<DmWorkerHeartbeatDO> {

    /**
     * query by workerId,one worker on heartbeat record
     */
    DmWorkerHeartbeatDO queryHeartbeatByWsn(String workerSeqNumber);

    /**
     * update gmt_modified,sidecar_send_time by worker_id
     */
    int updateHeartbeatByWsn(Date workerSendTime, WorkerHeartbeatType heartbeatType, String workerSeqNumber);
}

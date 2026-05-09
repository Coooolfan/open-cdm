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
import com.clougence.clouddm.console.web.dal.enumeration.FileStatus;
import com.clougence.clouddm.console.web.dal.model.DmFileDO;

/**
 * @author mode create time is 2020/3/2
 **/
public interface DmFileMapper extends BaseMapper<DmFileDO> {

    DmFileDO queryFileByUniqueId(String uniqueId);

    void updateStatusByUniqueId(String uniqueId, FileStatus toStatus, String message);

    void updateStatusByQueryId(String queryId, FileStatus toStatus, String message);

    List<DmFileDO> queryByAfterHeartbeatTime(Date point, int limit);

    void updateHeartbeatByUniqueId(String uniqueId);

    void updateAccessTimeByUniqueId(String uniqueId, String message);

    void incrementTryCountByUniqueId(String uniqueId, String message);

    int deleteFileByUniqueId(String uniqueId);

    int markFileDeletedByUniqueId(String uniqueId);
}

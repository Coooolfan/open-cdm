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
package com.clougence.clouddm.console.web.service.project;

import java.util.List;

import com.clougence.clouddm.console.web.dal.enumeration.ImType;
import com.clougence.clouddm.console.web.dal.model.DmMessengerDO;
import com.clougence.clouddm.console.web.model.fo.project.DevopsImAddFO;
import com.clougence.clouddm.console.web.model.fo.project.DevopsImUpdateFO;
import com.clougence.clouddm.console.web.service.project.domain.DmImDef;

public interface DmImService {

    List<DmImDef> getImDefList();

    List<DmMessengerDO> queryImList(String ownerUid);

    List<DmMessengerDO> queryMessengerByOwnerAndType(String ownerUid, ImType imType);

    DmMessengerDO queryImById(String ownerUid, long imId);

    void addIm(String ownerUid, DevopsImAddFO fo);

    void deleteImById(String ownerUid, long imId);

    void updateImById(String ownerUid, DevopsImUpdateFO fo);

    void testImByConfig(String ownerUid, DevopsImAddFO fo);
}

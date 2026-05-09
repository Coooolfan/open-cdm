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
package com.clougence.clouddm.init.component.fixtasks;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.clougence.clouddm.console.web.dal.enumeration.HostType;
import org.springframework.stereotype.Service;

import com.clougence.clouddm.base.metadata.ds.ConfigI18nKey;
import com.clougence.clouddm.console.web.dal.mapper.DmDsConfigMapper;
import com.clougence.clouddm.console.web.dal.mapper.DmDsKvBaseConfigMapper;
import com.clougence.clouddm.console.web.dal.mapper.RdpDataSourceMapper;
import com.clougence.clouddm.console.web.dal.model.DmDsConfigDO;
import com.clougence.clouddm.console.web.dal.model.DmDsKvBaseConfigDO;
import com.clougence.clouddm.console.web.dal.model.RdpDataSourceDO;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DmFixDmDsConfig {

    @Resource
    private RdpDataSourceMapper    rdpDataSourceMapper;
    @Resource
    private DmDsConfigMapper       dmDsConfigMapper;
    @Resource
    private DmDsKvBaseConfigMapper dmDsKvBaseConfigMapper;

    public void init() {
        List<DmDsConfigDO> dmDsConfigDOS = dmDsConfigMapper.selectList(null);
        if (dmDsConfigDOS.isEmpty()) {
            return;
        }

        Map<Long, DmDsConfigDO> dsConfigDOMap = dmDsConfigDOS.stream().collect(Collectors.toMap(DmDsConfigDO::getDataSourceId, dsDO -> dsDO));
        List<Long> dataSourceIdList = dmDsConfigDOS.stream().map(DmDsConfigDO::getDataSourceId).collect(Collectors.toList());
        Map<Long, RdpDataSourceDO> dsMap = rdpDataSourceMapper.listByIds(dataSourceIdList).stream().collect(Collectors.toMap(RdpDataSourceDO::getId, dsDO -> dsDO));

        List<DmDsKvBaseConfigDO> dsConfList = dmDsKvBaseConfigMapper.listByConfigName(ConfigI18nKey.CONFIG_RDB_CONN_HOST_DESCRIPTION.name());
        for (DmDsKvBaseConfigDO dmDsKvBaseConfigDO : dsConfList) {
            Long dataSourceId = dmDsKvBaseConfigDO.getDataSourceId();
            DmDsConfigDO dmDsConfigDO = dsConfigDOMap.get(dataSourceId);
            RdpDataSourceDO rdpDataSourceDO = dsMap.get(dataSourceId);
            if (dmDsKvBaseConfigDO.getConfigValue() != null && rdpDataSourceDO != null) {
                if (dmDsKvBaseConfigDO.getConfigValue().equals(rdpDataSourceDO.getPrivateHost())) {
                    dmDsConfigMapper.updateHostTypeById(dmDsConfigDO.getId(), HostType.PRIVATE);
                } else {
                    dmDsConfigMapper.updateHostTypeById(dmDsConfigDO.getId(), HostType.PUBLIC);
                }
            }
        }

    }
}

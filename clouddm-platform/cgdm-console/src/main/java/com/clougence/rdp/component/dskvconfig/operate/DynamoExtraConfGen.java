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
package com.clougence.rdp.component.dskvconfig.operate;

import java.util.List;

import org.springframework.stereotype.Service;

import com.clougence.clouddm.base.metadata.ds.DsExtraConfig;
import com.clougence.rdp.component.dskvconfig.RdpDsExtraConfGen;
import com.clougence.rdp.component.dskvconfig.model.DynamoExtraConfig;
import com.clougence.clouddm.console.web.model.fo.InitDsKvBaseConfigFO;
import com.clougence.clouddm.console.web.dal.model.RdpDataSourceDO;
import com.clougence.clouddm.console.web.dal.model.RdpDsKvBaseConfigDO;
import com.clougence.utils.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DynamoExtraConfGen implements RdpDsExtraConfGen {

    @Override
    public DynamoExtraConfig newDsExtraConfig() {
        return new DynamoExtraConfig();
    }

    @Override
    public DsExtraConfig genDsExtraConfig(RdpDataSourceDO dsDO, List<InitDsKvBaseConfigFO> fos) {
        DynamoExtraConfig config = newDsExtraConfig();

        for (InitDsKvBaseConfigFO f : fos) {
            fillEntry(config, f.getConfigName(), f.getConfigValue());
        }

        return config;
    }

    @Override
    public DsExtraConfig genDsExtraConfigFromExist(RdpDataSourceDO dsDO, List<RdpDsKvBaseConfigDO> confs) {
        DynamoExtraConfig config = newDsExtraConfig();
        for (RdpDsKvBaseConfigDO f : confs) {
            fillEntry(config, f.getConfigName(), f.getConfigValue());
        }

        return config;
    }

    protected void fillEntry(DynamoExtraConfig config, String key, String val) {
        if (key.equals(DynamoExtraConfig.Fields.httpsEnabled)) {
            if (StringUtils.isNotBlank(val)) {
                config.setHttpsEnabled(Boolean.parseBoolean(val));
            }
        }
    }
}

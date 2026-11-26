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
package com.clougence.clouddm.worker.component.workspace;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.clougence.clouddm.sdk.execute.meta.DsElement;
import com.clougence.clouddm.sdk.execute.meta.DsMetaService;
import com.clougence.schema.umi.special.rdb.RdbColumn;
import com.clougence.schema.umi.struts.UmiTypes;
import com.clougence.schema.umi.struts.Value;

public class WorkspaceMetaService implements DsMetaService {

    private final String version;

    public WorkspaceMetaService(String version){
        this.version = version;
    }

    @Override
    public void testConnect() {
    }

    @Override
    public String getVersion() { return this.version; }

    @Override
    public Map<String, String> getSqlParserParameters() { return Collections.emptyMap(); }

    @Override
    public String getCurrentCatalog() { return "memory"; }

    @Override
    public String getCurrentSchema() { return "main"; }

    @Override
    public List<DsElement> listLevels(List<UmiTypes> levels, Map<UmiTypes, Object> levelsParam) {
        return Collections.emptyList();
    }

    @Override
    public DsElement detailLevel(List<UmiTypes> levels, Map<UmiTypes, Object> levelsParam) {
        return null;
    }

    @Override
    public List<DsElement> listLeaf(Map<UmiTypes, Object> levelsParam, UmiTypes leafType, String pattern) {
        return Collections.emptyList();
    }

    @Override
    public Value detailLeaf(Map<UmiTypes, Object> levelsParam, UmiTypes leafType, String leafName) {
        return null;
    }

    @Override
    public Value fetchSelectObject(Map<UmiTypes, Object> levelsParam, String leafName) {
        return null;
    }

    @Override
    public Map<String, List<RdbColumn>> batchColumns(Map<UmiTypes, Object> levelsParam, UmiTypes leafType, List<String> leafNames) {
        return Collections.emptyMap();
    }

    @Override
    public String loadTableEditor(Map<UmiTypes, Object> levelsParam, String table) {
        return null;
    }

    @Override
    public List<String> requestObjectScript(Map<UmiTypes, Object> levelsParam, UmiTypes leafType, String leafName) {
        return Collections.emptyList();
    }
}

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
package com.clougence.clouddm.console.web.component.workspace;

import java.util.List;

import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceSourceKind;

import lombok.Getter;

@Getter
public class WorkspaceSourceRef {

    private final String              refId;
    private final WorkspaceSourceKind kind;
    private final String              dsName;
    private final List<String>        pathParts;
    private final String              filterExpr;
    private final String              rawQuery;

    public WorkspaceSourceRef(String refId, WorkspaceSourceKind kind, String dsName, List<String> pathParts, String filterExpr, String rawQuery){
        this.refId = refId;
        this.kind = kind;
        this.dsName = dsName;
        this.pathParts = pathParts;
        this.filterExpr = filterExpr;
        this.rawQuery = rawQuery;
    }
}

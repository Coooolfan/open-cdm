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
package com.clougence.clouddm.dsfamily.db2.dialect;

import com.clougence.clouddm.dsfamily.schema.dialect.AbstractDialect;
import com.clougence.utils.StringUtils;

public class Db2Dialect extends AbstractDialect {

    public static Db2Dialect INSTANCE = new Db2Dialect();

    @Override
    protected String keyWordsResource() {
        return "/META-INF/clougence/db-keywords/db2.keywords";
    }

    public String fmtComment(String str) {
        if (StringUtils.isBlank(str)) {
            return str;
        }

        return str.replace("'", "''");
    }

    @Override
    public String leftQualifier() {
        return "\"";
    }

    @Override
    public String rightQualifier() {
        return "\"";
    }
}

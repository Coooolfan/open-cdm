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
package com.clougence.clouddm.console.web.dal.enumeration;

import com.clougence.clouddm.console.web.constants.I18nDmLabelKeys;
import com.clougence.clouddm.sdk.model.analysis.TargetType;

import lombok.Getter;

/**
 * @author mode create time is 2023/05/21 13:27
 **/
@Getter
public enum RuleTarget {

    Catalog(I18nDmLabelKeys.RDB_CATALOG, TargetType.Catalog),
    Schema(I18nDmLabelKeys.RDB_SCHEMA, TargetType.Schema),
    Table(I18nDmLabelKeys.RDB_TABLE, TargetType.Table),
    View(I18nDmLabelKeys.RDB_VIEW, TargetType.View),
    Materialized(I18nDmLabelKeys.RDB_MATERIALIZED, TargetType.Materialized),
    Column(I18nDmLabelKeys.RDB_COLUMN, TargetType.Column),
    Index(I18nDmLabelKeys.RDB_INDEX, TargetType.Index),
    Constraint(I18nDmLabelKeys.RDB_CONSTRAINT, TargetType.Constraint),
    Sequence(I18nDmLabelKeys.RDB_SEQUENCE, TargetType.Sequence),
    Function(I18nDmLabelKeys.RDB_FUNCTION, TargetType.Function),
    Procedure(I18nDmLabelKeys.RDB_PROCEDURE, TargetType.Procedure),
    Trigger(I18nDmLabelKeys.RDB_TRIGGER, TargetType.Trigger),
    Synonym(I18nDmLabelKeys.RDB_SYNONYM, TargetType.Synonym),
    Key(I18nDmLabelKeys.CACHE_KEY, TargetType.Key),
    //
    Query(I18nDmLabelKeys.RDB_QUERY, TargetType.Query),
    Insert(I18nDmLabelKeys.RDB_INSERT, TargetType.Insert),
    Update(I18nDmLabelKeys.RDB_UPDATE, TargetType.Update),
    Delete(I18nDmLabelKeys.RDB_DELETE, TargetType.Delete),

    ;

    private final String     i18nKey;
    private final TargetType type;

    RuleTarget(I18nDmLabelKeys i18nKey, TargetType type){
        this.i18nKey = i18nKey.name();
        this.type = type;
    }

    public static RuleTarget ofTarget(TargetType type) {
        for (RuleTarget t : RuleTarget.values()) {
            if (t.type == type) {
                return t;
            }
        }
        return null;
    }
}

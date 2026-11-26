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

import org.junit.Assert;
import org.junit.Test;

import com.clougence.clouddm.api.common.exception.ErrorMessageException;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceSourceKind;

public class WorkspaceSqlRewriterTest {

    @Test
    public void testCrossDbJoinAndModifiers() {
        String sql = """
            -- comment with #FakeDs.t should be ignored
            SELECT u.id, o.amount, '#NotARef.tbl' AS tag
            FROM #"生产 PG 18".cdmgr.public.users.#filter($$status = 'ACTIVE' AND age > 18$$) u
            JOIN #MySQL_8.shop.orders.#filter('amount > 100') o ON u.id = o.user_id
            JOIN #Mongo_Prod.#query($$db.events.find({active: true})$$) e ON u.id = e.uid;
            """;

        List<WorkspaceStatementPlan> plans = WorkspaceSqlRewriter.parseAndRewrite(sql, false);
        Assert.assertEquals(1, plans.size());

        WorkspaceStatementPlan plan = plans.get(0);
        Assert.assertEquals(3, plan.getSourceRefs().size());
        Assert.assertTrue(plan.getRewrittenDuckDbSql().contains("cdm_remote_table('ref_1') u"));
        Assert.assertTrue(plan.getRewrittenDuckDbSql().contains("cdm_remote_table('ref_2') o"));
        Assert.assertTrue(plan.getRewrittenDuckDbSql().contains("cdm_remote_query('ref_3') e"));
        Assert.assertTrue(plan.getRewrittenDuckDbSql().contains("'#NotARef.tbl'"));

        WorkspaceSourceRef r1 = plan.getSourceRefs().get(0);
        Assert.assertEquals(WorkspaceSourceKind.TABLE, r1.getKind());
        Assert.assertEquals("生产 PG 18", r1.getDsName());
        Assert.assertEquals(List.of("cdmgr", "public", "users"), r1.getPathParts());
        Assert.assertEquals("status = 'ACTIVE' AND age > 18", r1.getFilterExpr());

        WorkspaceSourceRef r2 = plan.getSourceRefs().get(1);
        Assert.assertEquals(WorkspaceSourceKind.TABLE, r2.getKind());
        Assert.assertEquals("MySQL_8", r2.getDsName());
        Assert.assertEquals(List.of("shop", "orders"), r2.getPathParts());
        Assert.assertEquals("amount > 100", r2.getFilterExpr());

        WorkspaceSourceRef r3 = plan.getSourceRefs().get(2);
        Assert.assertEquals(WorkspaceSourceKind.QUERY, r3.getKind());
        Assert.assertEquals("Mongo_Prod", r3.getDsName());
        Assert.assertEquals("db.events.find({active: true})", r3.getRawQuery());
    }

    @Test
    public void testMultiStatementAndExplain() {
        String sql = "CREATE TEMP TABLE t1 AS SELECT 1 AS id; SELECT * FROM t1;";
        List<WorkspaceStatementPlan> plans = WorkspaceSqlRewriter.parseAndRewrite(sql, true);
        Assert.assertEquals(2, plans.size());
        Assert.assertTrue(plans.get(0).getRewrittenDuckDbSql().startsWith("EXPLAIN CREATE TEMP TABLE"));
        Assert.assertTrue(plans.get(1).getRewrittenDuckDbSql().startsWith("EXPLAIN SELECT * FROM t1"));
    }

    @Test
    public void testSecurityRejections() {
        assertForbidden("ATTACH '/tmp/evil.db' AS evil");
        assertForbidden("COPY (SELECT 1) TO '/tmp/out.csv'");
        assertForbidden("SET enable_external_access = true");
        assertForbidden("PRAGMA database_list");
        assertForbidden("SELECT * FROM read_csv('/etc/passwd')");
        assertForbidden("SELECT * FROM cdm_remote_table('ref_1')");
        assertForbidden("SELECT * FROM cdm_remote_query('ref_1')");
    }

    private static void assertForbidden(String sql) {
        try {
            WorkspaceSqlRewriter.parseAndRewrite(sql, false);
            Assert.fail("Expected ErrorMessageException for: " + sql);
        } catch (ErrorMessageException expected) {
            Assert.assertNotNull(expected.getMessage());
        }
    }
}

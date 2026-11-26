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

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.duckdb.DuckDBColumnType;
import org.duckdb.DuckDBDataChunkWriter;
import org.duckdb.DuckDBFunctions;
import org.duckdb.DuckDBTableFunction;
import org.duckdb.DuckDBTableFunctionBindInfo;
import org.duckdb.DuckDBTableFunctionCallInfo;
import org.duckdb.DuckDBTableFunctionInitInfo;
import org.junit.Assert;
import org.junit.Test;

import com.clougence.clouddm.worker.global.config.DmSidecarConfig;

public class WorkspaceSessionTest {

    @Test
    public void testDuckDbSandboxLockdownAndTempCleanup() throws Exception {
        File tempRoot = Files.createTempDirectory("cgdm-ws-test-").toFile();
        DmSidecarConfig cfg = new DmSidecarConfig();
        cfg.setWorkspaceEnabled(true);
        cfg.setWorkspaceMemoryLimit("256MB");
        cfg.setWorkspaceThreads(2);
        cfg.setWorkspaceTempDir(tempRoot.getAbsolutePath());
        cfg.setWorkspaceMaxTempDirSize("256MB");

        WorkspaceSession session = new WorkspaceSession("unit_sandbox_1", null, null, null, null, cfg);
        File sessionTempDir = new File(tempRoot, "ws_unit_sandbox_1");
        Assert.assertTrue("Session temp directory should exist while open", sessionTempDir.exists());

        try {
            // 1. Verify unauthorized refId cannot bypass table function
            WorkspaceQueryRequest unauthReq = new WorkspaceQueryRequest();
            unauthReq.setQueryId("q1");
            unauthReq.setQueryBody("SELECT * FROM cdm_remote_table('forged_ref')");
            try {
                session.executeQuery(unauthReq, new WorkspaceSourceResultBuilder(null, null, 100, false));
                Assert.fail("Forged refId must fail");
            } catch (RuntimeException expected) {
                Assert.assertTrue(expected.getMessage().contains("Unauthorized") || expected.getMessage().contains("forged_ref"));
            }

            // 2. Verify external access is locked down (cannot read local files)
            WorkspaceQueryRequest fileReq = new WorkspaceQueryRequest();
            fileReq.setQueryId("q2");
            fileReq.setQueryBody("SELECT * FROM read_csv('/etc/hosts')");
            try {
                session.executeQuery(fileReq, new WorkspaceSourceResultBuilder(null, null, 100, false));
                Assert.fail("External file access must be blocked by DuckDB sandbox");
            } catch (RuntimeException expected) {
                Assert.assertNotNull(expected.getMessage());
            }
        } finally {
            session.close();
        }

        Assert.assertFalse("Session temp directory should be cleaned on close", sessionTempDir.exists());
        tempRoot.delete();
    }

    @Test
    public void testDuckDbPureJavaTableFunctionProjectionPushdownAndJoin() throws Exception {
        Class.forName("org.duckdb.DuckDBDriver");
        AtomicBoolean pushedDownSingleCol = new AtomicBoolean(false);
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            DuckDBTableFunction<Object, long[], Object> leftFn = new DuckDBTableFunction<>() {
                @Override
                public Object bind(DuckDBTableFunctionBindInfo info) {
                    info.addResultColumn("id", DuckDBColumnType.BIGINT);
                    info.addResultColumn("name", DuckDBColumnType.VARCHAR);
                    info.addResultColumn("unused_blob", DuckDBColumnType.VARCHAR);
                    return new Object();
                }

                @Override
                public long[] init(DuckDBTableFunctionInitInfo info) {
                    info.setMaxThreads(1);
                    long count = info.getColumnCount();
                    long[] projected = new long[(int) count + 1];
                    projected[0] = 0; // cursor
                    for (int i = 0; i < count; i++) {
                        projected[i + 1] = info.getColumnIndex(i);
                    }
                    if (count == 2) {
                        pushedDownSingleCol.set(true);
                    }
                    return projected;
                }

                @Override
                public long apply(DuckDBTableFunctionCallInfo info, DuckDBDataChunkWriter output) {
                    long[] state = info.getInitData();
                    if (state[0] > 0) {
                        return 0;
                    }
                    state[0] = 1;
                    long[] ids = { 1L, 2L };
                    String[] names = { "Alice", "Bob" };
                    for (int r = 0; r < 2; r++) {
                        for (int c = 0; c < output.columnCount(); c++) {
                            long origCol = state[c + 1];
                            if (origCol == 0) {
                                output.vector(c).setLong(r, ids[r]);
                            } else if (origCol == 1) {
                                output.vector(c).setString(r, names[r]);
                            } else {
                                output.vector(c).setString(r, "BIG");
                            }
                        }
                    }
                    return 2;
                }
            };

            DuckDBFunctions.tableFunction()
                .withName("test_left_source")
                .withProjectionPushdown()
                .withFunction(leftFn)
                .register(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TEMP TABLE right_orders (user_id BIGINT, amount DOUBLE)");
                stmt.execute("INSERT INTO right_orders VALUES (1, 99.5), (2, 150.0)");

                List<String> rows = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery("SELECT l.name, r.amount FROM test_left_source() l JOIN right_orders r ON l.id = r.user_id ORDER BY l.id")) {
                    while (rs.next()) {
                        rows.add(rs.getString(1) + ":" + rs.getDouble(2));
                    }
                }
                Assert.assertEquals(List.of("Alice:99.5", "Bob:150.0"), rows);
                Assert.assertTrue("Projection pushdown should only request the 2 used columns (id, name)", pushedDownSingleCol.get());
            }
        }
    }
}

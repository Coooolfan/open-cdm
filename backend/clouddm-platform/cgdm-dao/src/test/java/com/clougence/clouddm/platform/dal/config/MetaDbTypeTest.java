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
package com.clougence.clouddm.platform.dal.config;

import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import com.baomidou.mybatisplus.annotation.DbType;
import com.clougence.utils.loader.CgClassLoader;

public class MetaDbTypeTest {

    @Test
    public void testFromJdbcUrl() {
        Assert.assertEquals(MetaDbType.MYSQL, MetaDbType.fromJdbcUrl("jdbc:mysql://127.0.0.1:3306/cdmgr"));
        Assert.assertEquals(MetaDbType.MYSQL, MetaDbType.fromJdbcUrl("  JDBC:MYSQL://127.0.0.1:3306/cdmgr?useSSL=false "));
        Assert.assertEquals(MetaDbType.POSTGRESQL, MetaDbType.fromJdbcUrl("jdbc:postgresql://127.0.0.1:5432/cdmgr"));
        Assert.assertEquals(MetaDbType.POSTGRESQL, MetaDbType.fromJdbcUrl("JDBC:POSTGRESQL://127.0.0.1:5432/cdmgr?sslmode=disable"));

        Assert.assertEquals(DbType.MYSQL, MetaDbType.MYSQL.getMyBatisPlusDbType());
        Assert.assertEquals(DbType.POSTGRE_SQL, MetaDbType.POSTGRESQL.getMyBatisPlusDbType());
        Assert.assertEquals("mysql", MetaDbType.MYSQL.getMyBatisDatabaseId());
        Assert.assertEquals("postgresql", MetaDbType.POSTGRESQL.getMyBatisDatabaseId());

        Assert.assertNull(MetaDbType.tryFromJdbcUrl(""));
        Assert.assertNull(MetaDbType.tryFromJdbcUrl("jdbc:oracle:thin:@127.0.0.1:1521:orcl"));
        try {
            MetaDbType.fromJdbcUrl("jdbc:oracle:thin:@127.0.0.1:1521:orcl");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("Unsupported metadata database"));
        }
    }

    @Test
    public void testConnectionPropertiesTimeoutUnits() {
        Properties mysqlProps = DmDalConfigDsFactory.buildConnectionProperties(MetaDbType.MYSQL, "jdbc:mysql://127.0.0.1:3306/cdmgr", "root", "123456", "10000");
        Assert.assertEquals("10000", mysqlProps.getProperty("connectTimeout"));
        Assert.assertEquals("30000", mysqlProps.getProperty("socketTimeout"));
        Assert.assertEquals("utf8", mysqlProps.getProperty("characterEncoding"));

        Properties pgProps = DmDalConfigDsFactory.buildConnectionProperties(MetaDbType.POSTGRESQL, "jdbc:postgresql://127.0.0.1:5432/cdmgr?sslmode=disable", "postgres", "123456", "10000");
        Assert.assertEquals("10", pgProps.getProperty("connectTimeout"));
        Assert.assertEquals("30", pgProps.getProperty("socketTimeout"));
        Assert.assertEquals("disable", pgProps.getProperty("sslmode"));
        Assert.assertNull(pgProps.getProperty("characterEncoding"));

        Properties pgSubSecondProps = DmDalConfigDsFactory.buildConnectionProperties(MetaDbType.POSTGRESQL, "jdbc:postgresql://127.0.0.1:5432/cdmgr", "postgres", "123456", "1500");
        Assert.assertEquals("2", pgSubSecondProps.getProperty("connectTimeout"));

        Properties pgExplicitTimeout = DmDalConfigDsFactory.buildConnectionProperties(MetaDbType.POSTGRESQL, "jdbc:postgresql://127.0.0.1:5432/cdmgr?connectTimeout=7", "postgres", "123456", "10000");
        Assert.assertEquals("7", pgExplicitTimeout.getProperty("connectTimeout"));
    }

    @Test
    public void testEnsureDualDriversAvailable() throws Exception {
        CgClassLoader mysqlLoader = DmDalConfig.ensureDriverAvailable(MetaDbType.MYSQL);
        Assert.assertNotNull(mysqlLoader.loadClass(MetaDbType.MYSQL.getDriverClassName()));

        CgClassLoader pgLoader = DmDalConfig.ensureDriverAvailable(MetaDbType.POSTGRESQL);
        Assert.assertNotNull(pgLoader.loadClass(MetaDbType.POSTGRESQL.getDriverClassName()));
        Assert.assertNotSame(mysqlLoader, pgLoader);
    }
}

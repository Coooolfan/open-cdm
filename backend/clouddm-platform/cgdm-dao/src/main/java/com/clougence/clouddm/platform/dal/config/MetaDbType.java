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

import java.util.Locale;

import com.baomidou.mybatisplus.annotation.DbType;
import com.clougence.utils.StringUtils;

import lombok.Getter;

@Getter
public enum MetaDbType {

    MYSQL(
        "MySQL",
        "jdbc:mysql:",
        "com.mysql.cj.jdbc.Driver",
        "driver-jar/mysql-connector-j-8.0.33.jar",
        "cgdm-mysq/mysql-connector-j-8.0.33.jar",
        DbType.MYSQL,
        "mysql",
        3306,
        "utf8mb4"
    ),
    POSTGRESQL(
        "PostgreSQL",
        "jdbc:postgresql:",
        "org.postgresql.Driver",
        "driver-jar/postgresql-42.7.10.jar",
        "cgdm-pg/postgresql-42.7.10.jar",
        DbType.POSTGRE_SQL,
        "postgresql",
        5432,
        "UTF8"
    );

    private final String displayName;
    private final String jdbcPrefix;
    private final String driverClassName;
    private final String driverJarResource;
    private final String driverJarCacheFile;
    private final DbType myBatisPlusDbType;
    private final String myBatisDatabaseId;
    private final int    defaultPort;
    private final String requiredCharset;

    MetaDbType(String displayName,
               String jdbcPrefix,
               String driverClassName,
               String driverJarResource,
               String driverJarCacheFile,
               DbType myBatisPlusDbType,
               String myBatisDatabaseId,
               int defaultPort,
               String requiredCharset){
        this.displayName = displayName;
        this.jdbcPrefix = jdbcPrefix;
        this.driverClassName = driverClassName;
        this.driverJarResource = driverJarResource;
        this.driverJarCacheFile = driverJarCacheFile;
        this.myBatisPlusDbType = myBatisPlusDbType;
        this.myBatisDatabaseId = myBatisDatabaseId;
        this.defaultPort = defaultPort;
        this.requiredCharset = requiredCharset;
    }

    public static MetaDbType fromJdbcUrl(String jdbcUrl) {
        if (StringUtils.isBlank(jdbcUrl)) {
            throw new IllegalArgumentException("jdbcUrl is blank.");
        }
        String normalized = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        for (MetaDbType type : values()) {
            if (normalized.startsWith(type.jdbcPrefix)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported metadata database jdbcUrl: " + jdbcUrl);
    }

    public static MetaDbType tryFromJdbcUrl(String jdbcUrl) {
        if (StringUtils.isBlank(jdbcUrl)) {
            return null;
        }
        String normalized = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        for (MetaDbType type : values()) {
            if (normalized.startsWith(type.jdbcPrefix)) {
                return type;
            }
        }
        return null;
    }

    public boolean isMysql() {
        return this == MYSQL;
    }

    public boolean isPostgresql() {
        return this == POSTGRESQL;
    }
}

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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

import com.clougence.drivers.DsConfigKeys;
import com.clougence.drivers.DsFactory;
import com.clougence.drivers.DsObject;
import com.clougence.utils.StringUtils;

public class DmDalConfigDsFactory implements DsFactory<Connection> {

    private static final String DEFAULT_MYSQL_CONNECT_TIMEOUT_MS = "3000";
    private static final String DEFAULT_MYSQL_SOCKET_TIMEOUT_MS  = "30000";
    private static final String DEFAULT_MYSQL_CHARACTER_ENCODING = "utf8";
    private static final String DEFAULT_PG_CONNECT_TIMEOUT_SEC   = "3";
    private static final String DEFAULT_PG_SOCKET_TIMEOUT_SEC    = "30";

    private final ClassLoader   driverClassLoader;
    private final MetaDbType    dbType;

    public DmDalConfigDsFactory(ClassLoader driverClassLoader){
        this(driverClassLoader, MetaDbType.MYSQL);
    }

    public DmDalConfigDsFactory(ClassLoader driverClassLoader, MetaDbType dbType){
        this.driverClassLoader = driverClassLoader;
        this.dbType = dbType == null ? MetaDbType.MYSQL : dbType;
    }

    @Override
    public DsObject<Connection> create(Properties dsConfig) throws Exception {
        String rawJdbcUrl = StringUtils.trimToNull(dsConfig.getProperty(DsConfigKeys.CUSTOM_URL.getConfigKey()));
        String jdbcUrl = normalizeJdbcUrl(rawJdbcUrl);
        String username = dsConfig.getProperty(DsConfigKeys.USER.getConfigKey());
        String password = dsConfig.getProperty(DsConfigKeys.PASSWORD.getConfigKey());
        if (jdbcUrl == null) {
            throw new IllegalArgumentException("jdbcUrl is blank.");
        }

        MetaDbType effectiveType = MetaDbType.tryFromJdbcUrl(rawJdbcUrl);
        if (effectiveType == null) {
            effectiveType = this.dbType;
        }

        Properties properties = buildConnectionProperties(effectiveType, rawJdbcUrl, username, password, dsConfig.getProperty(DsConfigKeys.LOGIN_TIMEOUT_MS.getConfigKey()));

        Driver driver = (Driver) this.driverClassLoader.loadClass(effectiveType.getDriverClassName()).getDeclaredConstructor().newInstance();
        Connection connection = driver.connect(jdbcUrl, properties);
        if (connection == null) {
            throw new SQLException(effectiveType.getDisplayName() + " driver refused jdbcUrl: " + jdbcUrl);
        }
        return new DsObject<>(dsConfig, connection, this);
    }

    static Properties buildConnectionProperties(MetaDbType dbType, String rawJdbcUrl, String username, String password, String loginTimeoutMsRaw) {
        Properties properties = new Properties();
        applyDefaultJdbcProperties(dbType, properties);
        collectJdbcUrlProperties(rawJdbcUrl, properties);

        if (StringUtils.isNotBlank(username)) {
            properties.setProperty("user", username);
        }
        if (password != null) {
            properties.setProperty("password", password);
        }

        String loginTimeoutMs = StringUtils.trimToNull(loginTimeoutMsRaw);
        if (loginTimeoutMs != null && !hasExplicitConnectTimeout(rawJdbcUrl)) {
            if (dbType.isPostgresql()) {
                properties.setProperty("connectTimeout", millisToCeilSeconds(loginTimeoutMs));
            } else {
                properties.setProperty("connectTimeout", loginTimeoutMs);
            }
        }
        return properties;
    }

    private static void applyDefaultJdbcProperties(MetaDbType dbType, Properties properties) {
        if (dbType.isPostgresql()) {
            properties.setProperty("connectTimeout", DEFAULT_PG_CONNECT_TIMEOUT_SEC);
            properties.setProperty("socketTimeout", DEFAULT_PG_SOCKET_TIMEOUT_SEC);
            return;
        }
        properties.setProperty("connectTimeout", DEFAULT_MYSQL_CONNECT_TIMEOUT_MS);
        properties.setProperty("socketTimeout", DEFAULT_MYSQL_SOCKET_TIMEOUT_MS);
        properties.setProperty("characterEncoding", DEFAULT_MYSQL_CHARACTER_ENCODING);
    }

    private static boolean hasExplicitConnectTimeout(String rawJdbcUrl) {
        if (StringUtils.isBlank(rawJdbcUrl)) {
            return false;
        }
        Properties urlProps = new Properties();
        collectJdbcUrlProperties(rawJdbcUrl, urlProps);
        return urlProps.containsKey("connectTimeout");
    }

    static String millisToCeilSeconds(String millisText) {
        try {
            long ms = Long.parseLong(millisText.trim());
            if (ms <= 0L) {
                return DEFAULT_PG_CONNECT_TIMEOUT_SEC;
            }
            long seconds = (ms + 999L) / 1000L;
            return Long.toString(Math.max(1L, seconds));
        } catch (NumberFormatException e) {
            return DEFAULT_PG_CONNECT_TIMEOUT_SEC;
        }
    }

    private static String normalizeJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        int queryIndex = jdbcUrl.indexOf('?');
        if (queryIndex < 0) {
            return jdbcUrl;
        }
        return StringUtils.trimToNull(jdbcUrl.substring(0, queryIndex));
    }

    private static void collectJdbcUrlProperties(String jdbcUrl, Properties properties) {
        if (StringUtils.isBlank(jdbcUrl)) {
            return;
        }
        int queryIndex = jdbcUrl.indexOf('?');
        if (queryIndex < 0 || queryIndex == jdbcUrl.length() - 1) {
            return;
        }
        String query = jdbcUrl.substring(queryIndex + 1);
        for (String pair : query.split("&")) {
            if (StringUtils.isBlank(pair)) {
                continue;
            }
            int separatorIndex = pair.indexOf('=');
            String key = separatorIndex < 0 ? pair : pair.substring(0, separatorIndex);
            String value = separatorIndex < 0 ? "" : pair.substring(separatorIndex + 1);
            key = decodeJdbcValue(key);
            if (StringUtils.isBlank(key)) {
                continue;
            }
            properties.setProperty(key, decodeJdbcValue(value));
        }
    }

    private static String decodeJdbcValue(String value) {
        return URLDecoder.decode(StringUtils.defaultString(value), StandardCharsets.UTF_8);
    }
}

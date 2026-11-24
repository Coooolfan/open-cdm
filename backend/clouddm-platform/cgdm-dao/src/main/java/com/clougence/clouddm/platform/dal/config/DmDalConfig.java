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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import javax.sql.DataSource;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.clougence.clouddm.api.common.GlobalConfUtils;
import com.clougence.drivers.DataSourceBridge;
import com.clougence.drivers.DsConfigKeys;
import com.clougence.utils.StringUtils;
import com.clougence.utils.io.IOUtils;
import com.clougence.utils.loader.CgClassLoader;
import com.clougence.utils.loader.providers.JarResourceLoader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * @author bucketli 2023/10/25 19:49:05
 */
@Slf4j
@Configuration
@EnableTransactionManagement
@MapperScan(basePackages = "com.clougence.clouddm.platform.dal.mapper", sqlSessionFactoryRef = "sqlSessionFactory")
public class DmDalConfig {

    public static final String                    MYSQL_DRIVER_CLASS_NAME = MetaDbType.MYSQL.getDriverClassName();
    public static final String                    PG_DRIVER_CLASS_NAME    = MetaDbType.POSTGRESQL.getDriverClassName();
    private static final Map<MetaDbType, CgClassLoader> DRIVER_CLASS_LOADERS = new EnumMap<>(MetaDbType.class);

    @Primary
    @Bean(name = "dataSource")
    public DataSource defaultDataSource(Environment env) {
        String jdbcUrl = env.getProperty("spring.datasource.jdbcurl");
        String jdbcUser = env.getProperty("spring.datasource.username");
        String password = env.getProperty("spring.datasource.password");
        long connectionTimeout = env.getProperty("spring.datasource.connection-timeout", Long.class, 10000L);
        Integer minimumIdle = env.getProperty("spring.datasource.minimum-idle", Integer.class, 1);
        Integer maximumPoolSize = env.getProperty("spring.datasource.maximum-pool-size", Integer.class, 20);

        jdbcUrl = StringUtils.trimToNull(jdbcUrl);
        jdbcUser = StringUtils.trimToNull(jdbcUser);
        if (jdbcUrl == null || jdbcUser == null) {
            throw new IllegalArgumentException("jdbcUrl/username is blank.");
        }

        MetaDbType dbType = MetaDbType.fromJdbcUrl(jdbcUrl);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDataSource(createDriverDataSource(jdbcUrl, jdbcUser, StringUtils.defaultString(password), connectionTimeout));
        hikariConfig.setConnectionTimeout(connectionTimeout);
        hikariConfig.setMinimumIdle(minimumIdle);
        hikariConfig.setMaximumPoolSize(maximumPoolSize);
        log.info("Default HikariCP datasource inited, metaDbType={}.", dbType);
        return new HikariDataSource(hikariConfig);
    }

    public static Connection createDriverConnection(String jdbcUrl, String username, String password, long connectionTimeout) throws SQLException {
        DataSource bridge = createDriverDataSource(jdbcUrl, username, password, connectionTimeout);
        return bridge.getConnection();
    }

    public static DataSource createDriverDataSource(String jdbcUrl, String username, String password, long connectionTimeout) {
        MetaDbType dbType = MetaDbType.fromJdbcUrl(jdbcUrl);
        CgClassLoader classLoader = ensureDriverAvailable(dbType);
        DmDalConfigDsFactory dsFactory = new DmDalConfigDsFactory(classLoader, dbType);

        Properties properties = new Properties();
        properties.setProperty(DsConfigKeys.CUSTOM_URL.getConfigKey(), jdbcUrl);
        properties.setProperty(DsConfigKeys.USER.getConfigKey(), username);
        properties.setProperty(DsConfigKeys.PASSWORD.getConfigKey(), StringUtils.defaultString(password));
        properties.setProperty(DsConfigKeys.LOGIN_TIMEOUT_MS.getConfigKey(), Long.toString(connectionTimeout));

        return new DataSourceBridge(properties, dsFactory);
    }

    public static synchronized CgClassLoader ensureDriverAvailable() {
        return ensureDriverAvailable(MetaDbType.MYSQL);
    }

    public static synchronized CgClassLoader ensureDriverAvailable(String jdbcUrl) {
        return ensureDriverAvailable(MetaDbType.fromJdbcUrl(jdbcUrl));
    }

    public static synchronized CgClassLoader ensureDriverAvailable(MetaDbType dbType) {
        MetaDbType targetType = dbType == null ? MetaDbType.MYSQL : dbType;
        String driverClassName = targetType.getDriverClassName();
        CgClassLoader cachedLoader = DRIVER_CLASS_LOADERS.get(targetType);
        if (cachedLoader != null) {
            try {
                cachedLoader.loadClass(driverClassName);
                return cachedLoader;
            } catch (ClassNotFoundException e) {
                log.debug("Cached runtime {} driver class is unavailable: {}", targetType.getDisplayName(), e.getMessage());
                IOUtils.closeQuietly(cachedLoader);
                DRIVER_CLASS_LOADERS.remove(targetType);
            }
        }

        CgClassLoader classLoader = createDriverClassLoader(targetType, false);
        try {
            classLoader.loadClass(driverClassName);
            DRIVER_CLASS_LOADERS.put(targetType, classLoader);
            return classLoader;
        } catch (ClassNotFoundException e) {
            log.debug("Runtime {} driver class is unavailable after loading cache: {}", targetType.getDisplayName(), e.getMessage());
            IOUtils.closeQuietly(classLoader);
        }

        classLoader = createDriverClassLoader(targetType, true);
        try {
            classLoader.loadClass(driverClassName);
            DRIVER_CLASS_LOADERS.put(targetType, classLoader);
            return classLoader;
        } catch (ClassNotFoundException e) {
            IOUtils.closeQuietly(classLoader);
            throw new IllegalStateException("Runtime " + targetType.getDisplayName() + " driver class is unavailable after refreshing cache: " + driverClassName, e);
        }
    }

    private static CgClassLoader createDriverClassLoader(MetaDbType dbType, boolean refresh) {
        File driverJarFile = new File(GlobalConfUtils.getTempData(dbType.getDriverJarCacheFile()));
        if (refresh) {
            try {
                Files.deleteIfExists(driverJarFile.toPath());
            } catch (IOException e) {
                throw new IllegalStateException("Delete runtime " + dbType.getDisplayName() + " driver cache failed: " + driverJarFile.getAbsolutePath(), e);
            }
        }
        if (!driverJarFile.isFile()) {
            copyDriverJarResource(dbType, driverJarFile);
        }
        if (!driverJarFile.isFile()) {
            throw new IllegalStateException("Runtime " + dbType.getDisplayName() + " driver jar is unavailable: " + driverJarFile.getAbsolutePath());
        }
        try {
            return new JarResourceLoader(driverJarFile).toClassLoader(DmDalConfig.class.getClassLoader());
        } catch (IOException e) {
            throw new IllegalStateException("Load runtime " + dbType.getDisplayName() + " driver jar failed: " + driverJarFile.getAbsolutePath(), e);
        }
    }

    private static void copyDriverJarResource(MetaDbType dbType, File driverJarFile) {
        String resourcePath = dbType.getDriverJarResource();
        try (InputStream input = DmDalConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                if (driverJarFile.isFile()) {
                    log.warn("Runtime {} driver resource {} not found in classpath, use existing file {}.", dbType.getDisplayName(), resourcePath, driverJarFile.getAbsolutePath());
                    return;
                }
                throw new IllegalStateException("Runtime " + dbType.getDisplayName() + " driver resource is missing: " + resourcePath);
            }

            File parentFile = driverJarFile.getParentFile();
            if (parentFile != null) {
                Files.createDirectories(parentFile.toPath());
            }
            Files.copy(input, driverJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Copy runtime " + dbType.getDisplayName() + " driver resource failed: " + resourcePath + " -> " + driverJarFile.getAbsolutePath(), e);
        }
    }

    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager txManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Primary
    @Bean(name = "sqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource, MybatisPlusInterceptor mybatisInterceptor, DatabaseIdProvider databaseIdProvider) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(resolveMapperLocations());
        factoryBean.setPlugins(mybatisInterceptor);
        factoryBean.setDatabaseIdProvider(databaseIdProvider);
        return factoryBean.getObject();
    }

    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        Properties properties = new Properties();
        properties.setProperty("MySQL", MetaDbType.MYSQL.getMyBatisDatabaseId());
        properties.setProperty("PostgreSQL", MetaDbType.POSTGRESQL.getMyBatisDatabaseId());
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        provider.setProperties(properties);
        return provider;
    }

    private Resource[] resolveMapperLocations() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<Resource> mapperResources = new ArrayList<>();
        Collections.addAll(mapperResources, resolver.getResources("classpath*:/mybatis/mapper/*.xml"));
        return mapperResources.toArray(new Resource[0]);
    }

    @Bean
    public PaginationInnerInterceptor pageInterceptor(Environment env) {
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor();
        paginationInterceptor.setMaxLimit(-1L);
        String jdbcUrl = env.getProperty("spring.datasource.jdbcurl");
        MetaDbType dbType = MetaDbType.tryFromJdbcUrl(jdbcUrl);
        DbType myBatisPlusDbType = dbType == null ? DbType.MYSQL : dbType.getMyBatisPlusDbType();
        paginationInterceptor.setDbType(myBatisPlusDbType);
        paginationInterceptor.setOptimizeJoin(true);
        return paginationInterceptor;
    }

    @Bean
    public MybatisPlusInterceptor mybatisInterceptor(PaginationInnerInterceptor pageInterceptor) {
        MybatisPlusInterceptor mybatisPlusInterceptor = new MybatisPlusInterceptor();
        mybatisPlusInterceptor.setInterceptors(Collections.singletonList(pageInterceptor));
        return mybatisPlusInterceptor;
    }
}

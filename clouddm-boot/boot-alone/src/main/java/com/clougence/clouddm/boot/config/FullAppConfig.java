package com.clougence.clouddm.boot.config;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@EnableCaching
@SpringBootConfiguration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableAutoConfiguration(exclude = { SecurityAutoConfiguration.class, DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class })
@ComponentScan(value = { "com.clougence.clouddm.boot", "com.clougence.clouddm.console.web", "com.clougence.clouddm.console.web.*", //
                         "com.clougence.clouddm.worker", "com.clougence.clouddm.worker.*",           //
                         "com.clougence.clouddm.comm.component.*", "com.clougence.clouddm.init",     //
                         "com.clougence.rdp.*" },//
        excludeFilters = { @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.clougence\\.clouddm\\.init\\.boot\\..*") })
public class FullAppConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        registry.addResourceHandler("doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}

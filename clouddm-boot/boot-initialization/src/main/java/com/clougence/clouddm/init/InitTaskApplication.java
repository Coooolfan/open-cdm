package com.clougence.clouddm.init;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 初始化阶段临时非 Web 任务容器。
 * 供 Flyway 和 fix 任务复用同一套 Spring 配置。
 */
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class, FlywayAutoConfiguration.class })
@ComponentScan(basePackages = { "com.clougence.clouddm.init.component.flyway", "com.clougence.clouddm.init.service.fix", "com.clougence.clouddm.console.web",
                                "com.clougence.clouddm.console.web.*", "com.clougence.rdp", "com.clougence.clouddm.base", "com.clougence.clouddm.platform",
                                "com.clougence.clouddm.sdk", "com.clougence.clouddm.api" })
public class InitTaskApplication {
}
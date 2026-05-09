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
package com.clougence.clouddm.boot.config;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
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

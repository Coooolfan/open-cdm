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
package com.clougence.clouddm.init.constant;

public final class InitSeedConstants {

    public static final String ADMIN_UID                  = "9999999999999999";
    public static final String DEFAULT_PRIMARY_EMAIL      = "admin@cdmgr.com";
    public static final String DEFAULT_PRIMARY_PASSWORD   = "123456";
    public static final String RUNTIME_ADMIN_EMAIL_KEY    = "clougence.init.admin.email";
    public static final String RUNTIME_ADMIN_PASSWORD_KEY = "clougence.init.admin.password";

    private InitSeedConstants(){
    }

    public static String resolveAdminEmail() {
        return defaultIfBlank(System.getProperty(RUNTIME_ADMIN_EMAIL_KEY), DEFAULT_PRIMARY_EMAIL);
    }

    public static String resolveAdminPassword() {
        return defaultIfBlank(System.getProperty(RUNTIME_ADMIN_PASSWORD_KEY), DEFAULT_PRIMARY_PASSWORD);
    }

    public static String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static String defaultIfBlank(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }
}

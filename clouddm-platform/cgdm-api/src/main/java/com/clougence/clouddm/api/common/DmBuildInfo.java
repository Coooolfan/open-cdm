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
package com.clougence.clouddm.api.common;

import com.clougence.utils.SystemUtils;

/** @author mode create time is 2021/1/5 **/
@Deprecated
public final class DmBuildInfo {

    public static final String BUILD_ID;
    public static final String BUILD_VERSION;
    public static final String PRODUCT_HOME;//todo

    static {
        BUILD_ID = SystemUtils.getSystemProperty("app.buildId", "Unknown");
        BUILD_VERSION = SystemUtils.getSystemProperty("app.buildVersion", "Unknown");
        PRODUCT_HOME = "https://www.clougence.com/clouddm-personal";
    }
}

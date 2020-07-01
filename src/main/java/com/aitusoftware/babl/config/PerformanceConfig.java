/*
 * Copyright 2019-2020 Aitu Software Limited.
 *
 * https://aitusoftware.com
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
package com.aitusoftware.babl.config;

public final class PerformanceConfig
{
    private PerformanceMode performanceMode = ConfigUtil.mapEnum(
        PerformanceMode::valueOf, Constants.PERFORMANCE_MODE_PROPERTY, Constants.PERFORMANCE_MODE_DEFAULT);

    /**
     * Returns the performance mode.
     * @return the performance mode.
     */
    public PerformanceMode performanceMode()
    {
        return performanceMode;
    }

    /**
     * Sets the performance mode.
     * @param performanceMode the performance mode
     * @return this for a fluent API
     */
    public PerformanceConfig performanceMode(final PerformanceMode performanceMode)
    {
        this.performanceMode = performanceMode;
        return this;
    }


    public static final class Constants
    {
        /**
         * System property that will be used to set the performance mode
         */
        public static final String PERFORMANCE_MODE_PROPERTY = "babl.performance.mode";

        /**
         * Default value for the performance mode
         */
        public static final PerformanceMode PERFORMANCE_MODE_DEFAULT = PerformanceMode.HIGH;
    }
}

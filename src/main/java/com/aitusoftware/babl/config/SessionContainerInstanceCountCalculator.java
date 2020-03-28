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

public final class SessionContainerInstanceCountCalculator
{
    public static final String CPU_COUNT_OVERRIDE_PROPERTY = "babl.override.cpu.count";
    private static final int CPU_COUNT = Integer.getInteger(
        CPU_COUNT_OVERRIDE_PROPERTY, Runtime.getRuntime().availableProcessors());
    // 1 CPU for application, 2 for OS
    private static final int RESERVED_CPUS = 3;
    // ~20% resource to allow for IRQ processing
    private static final float IRQ_PROCESSING_RATIO = 0.2f;

    public static int calculateSessionContainerCount(final SessionContainerConfig sessionContainerConfig)
    {
        if (sessionContainerConfig.autoScale())
        {
            return Math.max(1, (int)((CPU_COUNT - RESERVED_CPUS) * (1 - IRQ_PROCESSING_RATIO)));
        }
        return sessionContainerConfig.sessionContainerInstanceCount();
    }
}

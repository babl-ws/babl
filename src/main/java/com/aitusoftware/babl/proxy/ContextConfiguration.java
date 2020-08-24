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
package com.aitusoftware.babl.proxy;

import static com.aitusoftware.babl.proxy.IdleStrategies.backOff;
import static com.aitusoftware.babl.proxy.IdleStrategies.busySpin;
import static com.aitusoftware.babl.proxy.IdleStrategies.lowResource;

import java.util.concurrent.TimeUnit;

import com.aitusoftware.babl.config.PerformanceMode;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;

public final class ContextConfiguration
{
    public static void applySettings(
        final PerformanceMode performanceMode, final MediaDriver.Context mediaDriverContext)
    {
        switch (performanceMode)
        {
            case DEVELOPMENT:
            case LOW:
                mediaDriverContext
                    .clientLivenessTimeoutNs(TimeUnit.SECONDS.toNanos(7L))
                    .publicationUnblockTimeoutNs(TimeUnit.SECONDS.toNanos(8L))
                    .timerIntervalNs(TimeUnit.SECONDS.toNanos(6L))
                    .threadingMode(ThreadingMode.SHARED)
                    .sharedIdleStrategy(lowResource());
                break;
            case MEDIUM:
                mediaDriverContext
                    .clientLivenessTimeoutNs(TimeUnit.SECONDS.toNanos(2L))
                    .threadingMode(ThreadingMode.SHARED)
                    .termBufferSparseFile(false)
                    .sharedIdleStrategy(backOff());
                break;
            case HIGH:
                mediaDriverContext
                    .threadingMode(ThreadingMode.SHARED)
                    .termBufferSparseFile(false)
                    .sharedIdleStrategy(busySpin());
                break;
            default:
                // do nothing for CUSTOM mode
                break;
        }

    }

    public static void applySettings(final PerformanceMode performanceMode, final Aeron.Context aeronClientContext)
    {
        switch (performanceMode)
        {
            case DEVELOPMENT:
            case LOW:
                aeronClientContext
                    .driverTimeoutMs(TimeUnit.SECONDS.toNanos(10L))
                    .keepAliveIntervalNs(TimeUnit.SECONDS.toNanos(6L))
                    .awaitingIdleStrategy(lowResource())
                    .idleStrategy(lowResource());
                break;
            case MEDIUM:
                aeronClientContext
                    .driverTimeoutMs(TimeUnit.SECONDS.toNanos(2L))
                    .keepAliveIntervalNs(TimeUnit.SECONDS.toNanos(9L))
                    .preTouchMappedMemory(true)
                    .awaitingIdleStrategy(backOff())
                    .idleStrategy(backOff());
                break;
            case HIGH:
                aeronClientContext
                    .preTouchMappedMemory(true)
                    .awaitingIdleStrategy(backOff())
                    .idleStrategy(backOff());
                break;
            default:
                // do nothing for CUSTOM mode
                break;
        }

    }
}
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
package com.aitusoftware.babl.ext;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import com.aitusoftware.babl.monitoring.MappedApplicationAdapterStatistics;
import com.aitusoftware.babl.monitoring.MappedSessionContainerAdapterStatistics;
import com.aitusoftware.babl.monitoring.MappedSessionContainerStatistics;
import com.aitusoftware.babl.monitoring.MappedSessionStatistics;

import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;
import org.agrona.concurrent.errors.ErrorLogReader;

final class HealthCheckMonitoringConsumer implements MonitoringConsumer
{
    private static final long UPDATE_EXPIRY_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final NoOpErrorConsumer NO_OP_ERROR_CONSUMER = new NoOpErrorConsumer();

    private final EpochClock clock;
    private volatile boolean isHealthy = true;
    private volatile String reason = null;
    private volatile long lastUpdateMs;

    HealthCheckMonitoringConsumer(final EpochClock clock)
    {
        this.clock = clock;
        final Thread thread = new Thread(new HealthCheckEndpoint(this::isHealthy, this::reason, clock));
        thread.setDaemon(true);
        thread.start();
    }

    HealthCheckMonitoringConsumer()
    {
        this(new SystemEpochClock());
    }

    boolean isHealthy()
    {
        final long time = clock.time();

        checkUpdateExpiry(time);

        return isHealthy;
    }

    String reason()
    {
        return reason;
    }

    @Override
    public void applicationAdapterStatistics(
        final MappedApplicationAdapterStatistics applicationAdapterStatistics)
    {

    }

    @Override
    public void sessionAdapterStatistics(
        final MappedSessionContainerAdapterStatistics[] sessionAdapterStatistics)
    {

    }

    @Override
    public void errorBuffers(
        final MappedErrorBuffer[] errorBuffers)
    {
        for (final MappedErrorBuffer errorBuffer : errorBuffers)
        {
            if (ErrorLogReader.read(errorBuffer.errorBuffer(), NO_OP_ERROR_CONSUMER) != 0)
            {
                reason = "UNHANDLED_EXCEPTION";
                isHealthy = false;
            }
        }
        lastUpdateMs = clock.time();
    }

    @Override
    public void sessionContainerStatistics(
        final MappedSessionContainerStatistics[] sessionContainerStatistics)
    {
        for (final MappedSessionContainerStatistics stats : sessionContainerStatistics)
        {
            if (stats.timestamp() + UPDATE_EXPIRY_TIMEOUT_MS < clock.time())
            {
                reason = "SESSION_CONTAINER_HEARTBEAT_TIMEOUT";
                isHealthy = false;
            }
        }
        lastUpdateMs = clock.time();
    }

    @Override
    public void sessionStatistics(
        final Path statisticsFile, final MappedSessionStatistics sessionStatistics)
    {

    }

    private void checkUpdateExpiry(final long time)
    {
        if (lastUpdateMs != 0 && lastUpdateMs + UPDATE_EXPIRY_TIMEOUT_MS < time)
        {
            reason = "UPDATE_TIMEOUT";
            isHealthy = false;
        }
    }
}
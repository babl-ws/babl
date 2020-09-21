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
package com.aitusoftware.babl.websocket;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.aitusoftware.babl.monitoring.MappedSessionStatistics;
import com.aitusoftware.babl.monitoring.SessionContainerStatistics;
import com.aitusoftware.babl.monitoring.SessionStatisticsFileManager;
import com.aitusoftware.babl.pool.BufferPool;
import com.aitusoftware.babl.user.Application;
import com.aitusoftware.babl.config.SessionConfig;

import org.agrona.concurrent.EpochClock;

final class SessionFactory implements Supplier<WebSocketSession>
{
    private final SessionConfig sessionConfig;
    private final BufferPool bufferPool;
    private final EpochClock sharedClock;
    private final Application application;
    private final SessionContainerStatistics sessionContainerStatistics;
    private final SessionStatisticsFileManager sessionStatisticsManager;
    private final TrackingSessionDataListener sessionDataListener;

    SessionFactory(
        final SessionConfig sessionConfig,
        final BufferPool bufferPool,
        final EpochClock sharedClock,
        final Application application,
        final SessionContainerStatistics sessionContainerStatistics,
        final SessionStatisticsFileManager sessionStatisticsManager,
        final TrackingSessionDataListener sessionDataListener)
    {
        this.sessionConfig = sessionConfig;
        this.bufferPool = bufferPool;
        this.sharedClock = sharedClock;
        this.application = application;
        this.sessionContainerStatistics = sessionContainerStatistics;
        this.sessionStatisticsManager = sessionStatisticsManager;
        this.sessionDataListener = sessionDataListener;
    }

    @Override
    public WebSocketSession get()
    {
        final FilteringSessionDataListener perSessionDataListener =
            new FilteringSessionDataListener(sessionDataListener);
        final MappedSessionStatistics sessionStatistics = new MappedSessionStatistics();
        sessionStatisticsManager.assign(sessionStatistics);
        final FrameEncoder frameEncoder = new FrameEncoder(bufferPool, perSessionDataListener,
            sessionConfig, sessionContainerStatistics);
        final PingAgent pingAgent = new PingAgent(
            frameEncoder, sharedClock, TimeUnit.NANOSECONDS.toMillis(sessionConfig.pingIntervalNanos()),
            TimeUnit.NANOSECONDS.toMillis(sessionConfig.pongResponseTimeoutNanos()), perSessionDataListener);
        final FrameDecoder frameDecoder = new FrameDecoder(new MessageDispatcher(application), sessionConfig,
            bufferPool, pingAgent, true, sessionContainerStatistics);

        return new WebSocketSession(
            perSessionDataListener, frameDecoder,
            frameEncoder, sessionConfig, bufferPool, application, pingAgent,
            sharedClock, sessionStatistics, sessionContainerStatistics);
    }
}

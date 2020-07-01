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
package com.aitusoftware.babl.monitoring;

import java.util.function.LongConsumer;

import org.agrona.concurrent.EpochClock;

public final class EventLoopDurationReporter
{
    private static final long RESET_INTERVAL_MASK = ~(4095);

    private final EpochClock epochClock;
    private final LongConsumer durationReceiver;
    private long eventLoopStartMs;
    private long maxEventLoopDurationMs;
    private long lastResetMs;

    public EventLoopDurationReporter(
        final EpochClock epochClock,
        final LongConsumer durationReceiver)
    {
        this.epochClock = epochClock;
        this.durationReceiver = durationReceiver;
    }

    public void eventLoopStart()
    {
        eventLoopStartMs = epochClock.time();
    }

    public void eventLoopComplete()
    {
        final long timeMs = epochClock.time();
        if (((timeMs - lastResetMs) & RESET_INTERVAL_MASK) != 0)
        {
            maxEventLoopDurationMs = 0;
            lastResetMs = timeMs;
        }
        maxEventLoopDurationMs = Math.max(maxEventLoopDurationMs, timeMs - eventLoopStartMs);
        durationReceiver.accept(maxEventLoopDurationMs);
    }
}

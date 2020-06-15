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

import java.util.concurrent.TimeUnit;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

final class IdleStrategies
{
    private static final SleepingMillisIdleStrategy SLEEPING_MILLIS_IDLE_STRATEGY = new SleepingMillisIdleStrategy(1);
    private static final BusySpinIdleStrategy BUSY_SPIN_IDLE_STRATEGY = BusySpinIdleStrategy.INSTANCE;

    private IdleStrategies()
    {
    }

    static IdleStrategy lowResource()
    {
        return SLEEPING_MILLIS_IDLE_STRATEGY;
    }

    static IdleStrategy backOff()
    {
        return new BackoffIdleStrategy(0, 10, 10, TimeUnit.MICROSECONDS.toNanos(100));
    }

    static IdleStrategy busySpin()
    {
        return BUSY_SPIN_IDLE_STRATEGY;
    }
}
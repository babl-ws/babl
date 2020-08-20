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

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.Function;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;

final class ConfigUtil
{
    private ConfigUtil()
    {
    }

    static <T extends Enum<?>> T mapEnum(final Function<String, T> parser, final String property, final T defaultValue)
    {
        return Optional.ofNullable(System.getProperty(property)).map(parser).orElse(defaultValue);
    }

    static <T> T mapInstance(final Class<T> cls, final String property, final T defaultValue)
    {
        return Optional.ofNullable(System.getProperty(property)).map(instantiate(cls)).orElse(defaultValue);
    }

    static IdleStrategy mapIdleStrategy(
        final String property,
        final PerformanceMode performanceMode)
    {
        final String value = System.getProperty(property);
        if (value == null)
        {
            switch (performanceMode)
            {
                case HIGH:
                    return BusySpinIdleStrategy.INSTANCE;
                case MEDIUM:
                    return YieldingIdleStrategy.INSTANCE;
                case LOW:
                    return new SleepingMillisIdleStrategy(1L);
                default:
                    throw new IllegalArgumentException(performanceMode.name());
            }
        }
        return idleStrategyByName(value);
    }

    static IdleStrategy idleStrategyByName(final String value)
    {
        if ("BUSY_SPIN".equals(value))
        {
            return BusySpinIdleStrategy.INSTANCE;
        }
        else if ("YIELDING".equals(value))
        {
            return YieldingIdleStrategy.INSTANCE;
        }
        else if ("NO_OP".equals(value))
        {
            return NoOpIdleStrategy.INSTANCE;
        }
        else if ("SLEEPING".equals(value))
        {
            return new SleepingMillisIdleStrategy(1L);
        }
        else if ("BACK_OFF".equals(value))
        {
            return new BackoffIdleStrategy(50L, 150L, 1L, 1_000L);
        }
        throw new IllegalArgumentException("Unknown idle strategy: " + value);
    }

    static boolean mapBoolean(final String property, final boolean defaultValue)
    {
        final String value = System.getProperty(property);
        if (value == null)
        {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    @SuppressWarnings("unchecked")
    static <T> Function<String, T> instantiate(final Class<T> cls)
    {
        return className ->
        {
            try
            {
                return (T)Class.forName(className).getDeclaredConstructors()[0].newInstance();
            }
            catch (final InstantiationException | IllegalAccessException |
                InvocationTargetException | ClassNotFoundException e)
            {
                throw new IllegalArgumentException(String.format("Failed to instantiate %s to type %s",
                    className, cls.getName()), e);
            }
        };
    }
}

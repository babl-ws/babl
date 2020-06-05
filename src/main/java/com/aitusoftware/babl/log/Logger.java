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
package com.aitusoftware.babl.log;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Utility class for logging events to stdout for debugging purposes.
 */
public final class Logger
{
    public static final String DEBUG_ENABLED_PROPERTY = "babl.debug.enabled";
    private static final boolean LOGGING_ENABLED;
    private static final EnumSet<Category> ENABLED_CATEGORIES;

    static
    {
        final String debugEnabledValue = System.getProperty(DEBUG_ENABLED_PROPERTY);
        if ("true".equals(debugEnabledValue) || "all".equals(debugEnabledValue))
        {
            LOGGING_ENABLED = Boolean.getBoolean(DEBUG_ENABLED_PROPERTY);
            ENABLED_CATEGORIES = EnumSet.allOf(Category.class);
        }
        else if (debugEnabledValue == null || "".equals(debugEnabledValue))
        {
            LOGGING_ENABLED = false;
            ENABLED_CATEGORIES = EnumSet.noneOf(Category.class);
        }
        else
        {
            final List<Category> parsedCategories = new ArrayList<>();
            final String[] specified = debugEnabledValue.split(",");
            for (final String label : specified)
            {
                try
                {
                    final Category category = Category.valueOf(label);
                    parsedCategories.add(category);
                }
                catch (final RuntimeException e)
                {
                    // ignore
                }
            }
            LOGGING_ENABLED = true;
            ENABLED_CATEGORIES = parsedCategories.isEmpty() ?
                EnumSet.noneOf(Category.class) : EnumSet.copyOf(parsedCategories);
        }
    }

    public static void log(final Category category, final String format, final CharSequence arg0)
    {
        if (shouldIgnore(category))
        {
            return;
        }
        synchronized (Logger.class)
        {
            printMessagePrefix(category);
            System.out.printf(format, arg0);
        }
    }

    public static void log(
        final Category category, final String format, final CharSequence arg0, final CharSequence arg1)
    {
        if (shouldIgnore(category))
        {
            return;
        }
        synchronized (Logger.class)
        {
            printMessagePrefix(category);
            System.out.printf(format, arg0, arg1);
        }
    }

    public static void log(final Category category, final String format, final long arg0)
    {
        if (shouldIgnore(category))
        {
            return;
        }
        synchronized (Logger.class)
        {
            printMessagePrefix(category);
            System.out.printf(format, arg0);
        }
    }

    public static void log(final Category category, final String format, final long arg0, final CharSequence arg1)
    {
        if (shouldIgnore(category))
        {
            return;
        }
        synchronized (Logger.class)
        {
            printMessagePrefix(category);
            System.out.printf(format, arg0, arg1);
        }
    }

    public static void log(final Category category, final String format, final long arg0, final long arg1)
    {
        if (shouldIgnore(category))
        {
            return;
        }
        synchronized (Logger.class)
        {
            printMessagePrefix(category);
            System.out.printf(format, arg0, arg1);
        }
    }

    private static void printMessagePrefix(final Category category)
    {
        System.out.print(LocalDateTime.now() + " ");
        System.out.printf("[%s] ", Thread.currentThread().getName());
        System.out.print(category + ": ");
    }

    private static boolean shouldIgnore(final Category category)
    {
        return !LOGGING_ENABLED || !ENABLED_CATEGORIES.contains(category);
    }
}
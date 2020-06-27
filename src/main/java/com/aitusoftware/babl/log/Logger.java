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

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    public static final String DEBUG_FILE_PROPERTY = "babl.debug.file";
    private static final boolean LOGGING_ENABLED;
    private static final EnumSet<Category> ENABLED_CATEGORIES;
    private static final PrintStream OUTPUT_STREAM;

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
        final String debugFile = System.getProperty(DEBUG_FILE_PROPERTY);
        if (debugFile != null)
        {
            try
            {
                Files.createDirectories(Paths.get(debugFile).getParent());
                OUTPUT_STREAM = new PrintStream(debugFile);
            }
            catch (final IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }
        else
        {
            OUTPUT_STREAM = System.out;
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
            OUTPUT_STREAM.printf(format, arg0);
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
            OUTPUT_STREAM.printf(format, arg0, arg1);
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
            OUTPUT_STREAM.printf(format, arg0);
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
            OUTPUT_STREAM.printf(format, arg0, arg1);
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
            OUTPUT_STREAM.printf(format, arg0, arg1);
        }
    }

    private static void printMessagePrefix(final Category category)
    {
        OUTPUT_STREAM.print(LocalDateTime.now() + " ");
        OUTPUT_STREAM.printf("[%s] ", Thread.currentThread().getName());
        OUTPUT_STREAM.print(category + ": ");
    }

    private static boolean shouldIgnore(final Category category)
    {
        return !LOGGING_ENABLED || !ENABLED_CATEGORIES.contains(category);
    }
}
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public final class SessionContainerAdapterStatisticsPrinter
{
    public static void main(final String[] args)
    {
        sessionContainerAdapter(Paths.get(args[0]), stats ->
        {
            System.out.printf("%s%n", args[0]);
            System.out.printf("Poll Limit Reached: %d%n", stats.pollLimitReachedCount());
            System.out.printf("Proxy Back-Pressure Count: %d%n", stats.sessionBackPressureCount());
        });
    }

    private static void sessionContainerAdapter(
        final Path file,
        final Consumer<MappedSessionContainerAdapterStatistics> consumer)
    {
        try (MappedFile mappedFile = new MappedFile(
            file, MappedSessionContainerAdapterStatistics.LENGTH, false))
        {
            final MappedSessionContainerAdapterStatistics statistics =
                new MappedSessionContainerAdapterStatistics(mappedFile);
            consumer.accept(statistics);
        }
    }
}

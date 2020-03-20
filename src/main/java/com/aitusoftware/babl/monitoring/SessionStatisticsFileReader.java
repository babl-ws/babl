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

import java.nio.MappedByteBuffer;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.agrona.IoUtil;
import org.agrona.concurrent.UnsafeBuffer;

final class SessionStatisticsFileReader
{
    static void readEntries(final Path file, final Consumer<MappedSessionStatistics> statisticsConsumer)
    {
        final long length = file.toFile().length();
        final MappedByteBuffer mappedByteBuffer = IoUtil.mapExistingFile(file.toFile(), "session-statistics");
        try
        {
            final UnsafeBuffer buffer = new UnsafeBuffer(mappedByteBuffer);
            int offset = 0;
            final MappedSessionStatistics statistics = new MappedSessionStatistics();
            for (; offset < length; offset += MappedSessionStatistics.LENGTH)
            {
                statistics.wrap(buffer, offset);
                if (statistics.sessionId() != MappedSessionStatistics.NULL_SESSION_ID &&
                    statistics.sessionId() != -1L)
                {
                    statisticsConsumer.accept(statistics);
                }
            }
        }
        finally
        {
            IoUtil.unmap(mappedByteBuffer);
        }
    }
}
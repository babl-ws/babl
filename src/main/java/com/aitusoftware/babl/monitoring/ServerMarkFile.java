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

import org.agrona.BitUtil;
import org.agrona.MarkFile;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.SystemEpochClock;
import org.agrona.concurrent.UnsafeBuffer;

public final class ServerMarkFile extends MarkFile
{
    public static final int DATA_OFFSET = BitUtil.SIZE_OF_INT + BitUtil.SIZE_OF_LONG;
    public static final int DATA_LENGTH = MappedSessionContainerStatistics.LENGTH;
    public static final int ERROR_BUFFER_OFFSET = BitUtil.align(DATA_OFFSET + DATA_LENGTH,
        BitUtil.CACHE_LINE_LENGTH);
    public static final int ERROR_BUFFER_LENGTH = 65536;
    public static final String MARK_FILE_NAME = "babl-server.mark";
    private static final int TOTAL_LENGTH = ERROR_BUFFER_OFFSET + ERROR_BUFFER_LENGTH;

    public ServerMarkFile(final Path directory)
    {
        super(directory.resolve(MARK_FILE_NAME).toFile(), false,
            0, BitUtil.SIZE_OF_INT, TOTAL_LENGTH, 5_000L,
            new SystemEpochClock(), v -> {}, System.out::println);
    }

    public AtomicBuffer errorBuffer()
    {
        return new UnsafeBuffer(buffer(), ERROR_BUFFER_OFFSET, ERROR_BUFFER_LENGTH);
    }

    public AtomicBuffer serverStatisticsBuffer()
    {
        return new UnsafeBuffer(buffer(), DATA_OFFSET, DATA_LENGTH);
    }
}
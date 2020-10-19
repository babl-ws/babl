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

import java.io.Closeable;

import org.agrona.BitUtil;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AtomicBuffer;

public final class MappedBroadcastStatistics extends BroadcastStatistics implements Closeable
{
    private static final int SESSION_BACK_PRESSURE_COUNT_OFFSET = 0;
    private static final int TOPIC_COUNT_OFFSET = SESSION_BACK_PRESSURE_COUNT_OFFSET + BitUtil.SIZE_OF_LONG;
    public static final int LENGTH = TOPIC_COUNT_OFFSET + BitUtil.SIZE_OF_INT;
    public static final String FILE_NAME = "broadcast-stats.data";

    private final MappedFile mappedFile;
    private final AtomicBuffer buffer;

    private long sessionBackPressureCount;

    public MappedBroadcastStatistics(
        final MappedFile mappedFile)
    {
        this.mappedFile = mappedFile;
        this.buffer = mappedFile.buffer();
    }

    @Override
    public void broadcastSessionBackPressure()
    {
        sessionBackPressureCount++;
        buffer.putLongOrdered(SESSION_BACK_PRESSURE_COUNT_OFFSET, sessionBackPressureCount);
    }

    @Override
    public void topicCount(final int count)
    {
        buffer.putIntOrdered(TOPIC_COUNT_OFFSET, count);
    }

    public long broadcastSessionBackPressureCount()
    {
        return buffer.getLongVolatile(SESSION_BACK_PRESSURE_COUNT_OFFSET);
    }

    public int topicCount()
    {
        return buffer.getIntVolatile(TOPIC_COUNT_OFFSET);
    }

    @Override
    public void close()
    {
        CloseHelper.close(mappedFile);
    }
}
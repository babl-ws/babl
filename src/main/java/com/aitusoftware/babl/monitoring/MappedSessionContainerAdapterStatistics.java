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

public final class MappedSessionContainerAdapterStatistics
    extends SessionContainerAdapterStatistics implements Closeable
{
    private static final int POLL_LIMIT_REACHED_OFFSET = 0;
    private static final int SESSION_BACK_PRESSURE_OFFSET = POLL_LIMIT_REACHED_OFFSET + BitUtil.SIZE_OF_LONG;
    public static final int LENGTH = SESSION_BACK_PRESSURE_OFFSET + BitUtil.SIZE_OF_LONG;
    public static final String FILE_NAME = "session-adapter-stats.data";

    private final MappedFile mappedFile;
    private final AtomicBuffer buffer;
    private final int offset;

    private long pollLimitReachedCount;
    private long sessionBackPressureCount;

    public MappedSessionContainerAdapterStatistics(
        final MappedFile mappedFile)
    {
        this.mappedFile = mappedFile;
        this.buffer = mappedFile.buffer();
        this.offset = 0;
    }

    @Override
    public void adapterPollLimitReached()
    {
        pollLimitReachedCount++;
        updatePollLimitReachedCount(pollLimitReachedCount);
    }

    @Override
    public void onSessionBackPressure()
    {
        sessionBackPressureCount++;
        updateSessionBackPressureCount(sessionBackPressureCount);
    }

    public void reset()
    {
        updatePollLimitReachedCount(0);
        updateSessionBackPressureCount(0);
    }

    public long pollLimitReachedCount()
    {
        return buffer.getLongVolatile(toOffset(POLL_LIMIT_REACHED_OFFSET));
    }

    public long sessionBackPressureCount()
    {
        return buffer.getLongVolatile(toOffset(SESSION_BACK_PRESSURE_OFFSET));
    }

    private void updateSessionBackPressureCount(final long sessionBackPressureCount)
    {
        buffer.putLongOrdered(toOffset(SESSION_BACK_PRESSURE_OFFSET), sessionBackPressureCount);
    }

    private void updatePollLimitReachedCount(final long pollLimitReachedCount)
    {
        buffer.putLongOrdered(toOffset(POLL_LIMIT_REACHED_OFFSET), pollLimitReachedCount);
    }

    private int toOffset(final int delta)
    {
        return offset + delta;
    }

    @Override
    public void close()
    {
        CloseHelper.close(mappedFile);
    }
}

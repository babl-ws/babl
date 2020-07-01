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

import org.agrona.BitUtil;
import org.agrona.concurrent.AtomicBuffer;

public final class MappedSessionContainerStatistics extends SessionContainerStatistics
{
    private static final int ACTIVITY_TIMESTAMP_OFFSET = 0;
    private static final int BYTES_READ_OFFSET = ACTIVITY_TIMESTAMP_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int BYTES_WRITTEN_OFFSET = BYTES_READ_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int ACTIVE_SESSION_COUNT_OFFSET = BYTES_WRITTEN_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int RECEIVE_BACK_PRESSURE_EVENTS_OFFSET = ACTIVE_SESSION_COUNT_OFFSET + BitUtil.SIZE_OF_INT;
    private static final int INVALID_OPCODE_EVENTS_OFFSET = RECEIVE_BACK_PRESSURE_EVENTS_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int MAX_EVENT_LOOP_DURATION_MS_OFFSET = INVALID_OPCODE_EVENTS_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int PROXY_BACK_PRESSURE_EVENTS_OFFSET = MAX_EVENT_LOOP_DURATION_MS_OFFSET +
        BitUtil.SIZE_OF_LONG;
    private static final int PROXY_BACK_PRESSURED_OFFSET = PROXY_BACK_PRESSURE_EVENTS_OFFSET + BitUtil.SIZE_OF_LONG;
    public static final int LENGTH = PROXY_BACK_PRESSURED_OFFSET + BitUtil.SIZE_OF_INT;

    private final AtomicBuffer buffer;
    private final int offset;

    private long bytesRead;
    private long bytesWritten;
    private long receiveBackPressureEvents;
    private long invalidOpCodeEvents;
    private long proxyBackPressureEvents;

    public MappedSessionContainerStatistics(
        final AtomicBuffer buffer,
        final int offset)
    {
        this.buffer = buffer;
        this.offset = offset;
        bytesRead = bytesRead();
        bytesWritten = bytesWritten();
    }

    @Override
    public void heartbeat(final long timestamp)
    {
        buffer.putLongOrdered(toOffset(ACTIVITY_TIMESTAMP_OFFSET), timestamp);
    }

    public long timestamp()
    {
        return buffer.getLongVolatile(toOffset(ACTIVITY_TIMESTAMP_OFFSET));
    }

    @Override
    public void eventLoopDurationMs(final long eventLoopDurationMs)
    {
        buffer.putLongOrdered(toOffset(MAX_EVENT_LOOP_DURATION_MS_OFFSET), eventLoopDurationMs);
    }

    public long maxEventLoopDurationMs()
    {
        return buffer.getLongVolatile(toOffset(MAX_EVENT_LOOP_DURATION_MS_OFFSET));
    }

    @Override
    public void bytesRead(final int bytesRead)
    {
        if (bytesRead > 0)
        {
            this.bytesRead += bytesRead;
            buffer.putLongOrdered(toOffset(BYTES_READ_OFFSET), this.bytesRead);
        }
    }

    public long bytesRead()
    {
        return buffer.getLongVolatile(toOffset(BYTES_READ_OFFSET));
    }

    @Override
    public void bytesWritten(final int bytesWritten)
    {
        if (bytesWritten > 0)
        {
            this.bytesWritten += bytesWritten;
            buffer.putLongOrdered(toOffset(BYTES_WRITTEN_OFFSET), this.bytesWritten);
        }
    }

    public long bytesWritten()
    {
        return buffer.getLongVolatile(toOffset(BYTES_WRITTEN_OFFSET));
    }

    @Override
    public void activeSessionCount(final int activeSessionCount)
    {
        buffer.putIntOrdered(toOffset(ACTIVE_SESSION_COUNT_OFFSET), activeSessionCount);
    }

    public int activeSessionCount()
    {
        return buffer.getIntVolatile(toOffset(ACTIVE_SESSION_COUNT_OFFSET));
    }

    @Override
    public void receiveBackPressure()
    {
        receiveBackPressureEvents++;
        buffer.putLongOrdered(toOffset(RECEIVE_BACK_PRESSURE_EVENTS_OFFSET), receiveBackPressureEvents);
    }

    public long receiveBackPressureEvents()
    {
        return buffer.getLongVolatile(toOffset(RECEIVE_BACK_PRESSURE_EVENTS_OFFSET));
    }

    @Override
    public void invalidOpCode()
    {
        invalidOpCodeEvents++;
        buffer.putLongOrdered(toOffset(INVALID_OPCODE_EVENTS_OFFSET), invalidOpCodeEvents);
    }

    public long invalidOpCodeEvents()
    {
        return buffer.getLongVolatile(toOffset(INVALID_OPCODE_EVENTS_OFFSET));
    }

    @Override
    public void onProxyBackPressure()
    {
        proxyBackPressureEvents++;
        buffer.putLongOrdered(toOffset(PROXY_BACK_PRESSURE_EVENTS_OFFSET), proxyBackPressureEvents);
    }

    public long proxyBackPressureEvents()
    {
        return buffer.getLongVolatile(toOffset(PROXY_BACK_PRESSURE_EVENTS_OFFSET));
    }

    @Override
    public void proxyBackPressured(final int isBackPressured)
    {
        buffer.putIntOrdered(toOffset(PROXY_BACK_PRESSURED_OFFSET), isBackPressured);
    }

    public boolean isProxyBackPressured()
    {
        return BackPressureStatus.NOT_BACK_PRESSURED != buffer.getIntVolatile(toOffset(PROXY_BACK_PRESSURED_OFFSET));
    }

    private int toOffset(final int offset)
    {
        return this.offset + offset;
    }
}

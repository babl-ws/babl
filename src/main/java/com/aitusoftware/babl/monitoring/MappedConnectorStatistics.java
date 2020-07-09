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

public final class MappedConnectorStatistics extends ConnectorStatistics implements Closeable
{
    private static final int REJECTED_COUNT_OFFSET = 0;
    private static final int ACCEPTED_COUNT_OFFSET = REJECTED_COUNT_OFFSET + BitUtil.SIZE_OF_LONG;
    public static final int LENGTH = ACCEPTED_COUNT_OFFSET + BitUtil.SIZE_OF_LONG;
    public static final String FILE_NAME = "connector-stats.data";

    private final MappedFile mappedFile;
    private final AtomicBuffer buffer;
    private final int offset;

    private long rejectionCount;
    private long acceptedCount;

    public MappedConnectorStatistics(
        final MappedFile mappedFile)
    {
        this.mappedFile = mappedFile;
        this.buffer = mappedFile.buffer();
        this.offset = 0;
    }

    @Override
    public void onConnectionRejected()
    {
        rejectionCount++;
        updateRejectedCount(rejectionCount);
    }

    @Override
    public void onConnectionAccepted()
    {
        acceptedCount++;
        updateRejectedCount(acceptedCount);
    }

    public long rejectedCount()
    {
        return buffer.getLongVolatile(toOffset(REJECTED_COUNT_OFFSET));
    }

    private void updateRejectedCount(final long rejectedCount)
    {
        buffer.putLongOrdered(toOffset(REJECTED_COUNT_OFFSET), rejectedCount);
    }

    public long acceptedCount()
    {
        return buffer.getLongVolatile(toOffset(ACCEPTED_COUNT_OFFSET));
    }

    private void updateAcceptedCount(final long acceptedCount)
    {
        buffer.putLongOrdered(toOffset(ACCEPTED_COUNT_OFFSET), acceptedCount);
    }

    public void reset()
    {
        updateRejectedCount(0);
        updateAcceptedCount(0);
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

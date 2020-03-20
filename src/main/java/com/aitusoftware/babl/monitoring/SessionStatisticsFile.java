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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import com.aitusoftware.babl.websocket.WebSocketSession;

import org.agrona.BitUtil;
import org.agrona.IoUtil;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class SessionStatisticsFile implements AutoCloseable
{
    private static final String FILENAME_PREFIX = "babl-session-statistics-";
    private static final String FILENAME_SUFFIX = ".data";
    private static final Pattern FILENAME_PATTERN = Pattern.compile(FILENAME_PREFIX + "\\d+" + FILENAME_SUFFIX);
    private static final String FILENAME_FORMAT = FILENAME_PREFIX + "%d" + FILENAME_SUFFIX;
    private final int entryCount;
    private final int length;
    private final long[] freeList;
    private final Path file;
    private int size;

    private final MappedByteBuffer mappedByteBuffer;
    private final AtomicBuffer buffer;

    public SessionStatisticsFile(final Path file, final int entryCount)
    {
        this.file = file;
        if (Files.exists(file))
        {
            try
            {
                Files.delete(file);
            }
            catch (final IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }
        this.entryCount = BitUtil.findNextPositivePowerOfTwo(entryCount);
        this.length = (this.entryCount * MappedSessionStatistics.LENGTH);
        mappedByteBuffer = IoUtil.mapNewFile(file.toFile(), length);
        buffer = new UnsafeBuffer(mappedByteBuffer);
        freeList = new long[this.entryCount / BitUtil.SIZE_OF_LONG];
    }

    private int findFreeSlot()
    {
        for (int i = 0; i < freeList.length; i++)
        {
            if (BitSet.hasZeroBit(freeList[i]))
            {
                final int bitIndex = BitSet.indexOfZeroBit(freeList[i]);
                freeList[i] = BitSet.setBit(freeList[i], bitIndex);
                return i * BitUtil.SIZE_OF_LONG + bitIndex;
            }
        }
        throw new IllegalStateException("File is full");
    }

    private void freeSlot(final int index)
    {
        final int maskIndex = index / BitUtil.SIZE_OF_LONG;
        final int bitIndex = index & 63;
        freeList[maskIndex] = BitSet.clearBit(freeList[maskIndex], bitIndex);
    }

    public boolean assign(final MappedSessionStatistics sessionStatistics)
    {
        if (size == entryCount)
        {
            return false;
        }
        sessionStatistics.set(buffer, bufferOffset(findFreeSlot()), -1, WebSocketSession.SessionState.CONNECTED);
        size++;
        return true;
    }

    public void remove(final MappedSessionStatistics sessionStatistics)
    {
        final int slotIndex = slotIndex(sessionStatistics.offset());
        freeSlot(slotIndex);
        size--;
        sessionStatistics.removed();
    }

    public int size()
    {
        return size;
    }

    public boolean isFull()
    {
        return size == entryCount;
    }

    @Override
    public void close()
    {
        IoUtil.unmap(mappedByteBuffer);
    }

    public static String filename(final int fileIndex)
    {
        return String.format(FILENAME_FORMAT, fileIndex);
    }

    public static boolean isSessionStatisticsFile(final Path path)
    {
        return FILENAME_PATTERN.matcher(path.getFileName().toString()).matches();
    }

    private static int bufferOffset(final int index)
    {
        return (index * MappedSessionStatistics.LENGTH);
    }

    private static int slotIndex(final int offset)
    {
        return (offset) / MappedSessionStatistics.LENGTH;
    }

    @Override
    public String toString()
    {
        return "SessionStatisticsFile{" +
            "entryCount=" + entryCount +
            ", file=" + file +
            '}';
    }
}

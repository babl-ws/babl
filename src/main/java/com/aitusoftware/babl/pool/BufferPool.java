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
package com.aitusoftware.babl.pool;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.function.IntFunction;

import org.agrona.concurrent.UnsafeBuffer;

/**
 * Component to enabling pooling of {@code ByteBuffer} instances.
 */
public final class BufferPool implements AutoCloseable
{
    private static final int POOL_COUNT = 31;
    private static final byte ZERO = (byte)0;

    private final IntFunction<ByteBuffer> bufferFactory = ByteBuffer::allocateDirect;
    @SuppressWarnings("unchecked")
    private final Deque<ByteBuffer>[] buffersBySize = new Deque[POOL_COUNT];
    private final UnsafeBuffer bufferWrapper = new UnsafeBuffer();
    private final boolean clearOnRelease;

    /**
     * Constructor for an instance that does not clear buffer contents on release.
     */
    public BufferPool()
    {
        this(false);
    }

    /**
     * Constructor for an instance with configurable release behaviour.
     *
     * @param clearOnRelease indicates whether buffer contents should be zeroed on release
     */
    public BufferPool(final boolean clearOnRelease)
    {
        this.clearOnRelease = clearOnRelease;
        Arrays.setAll(buffersBySize, ArrayDeque::new);
    }

    /**
     * Acquire a {@code ByteBuffer} of the specified size; a new instance will be created if none exists.
     *
     * @param size the required size of the buffer, must be a power-of-two
     * @return the buffer
     */
    public ByteBuffer acquire(final int size)
    {
        validateBufferSize(size);
        final Deque<ByteBuffer> bufferPool = bufferPool(size);
        if (bufferPool.isEmpty())
        {
            return bufferFactory.apply(size);
        }
        return bufferPool.removeLast();
    }

    /**
     * Releases a buffer back to the pool.
     *
     * @param buffer the buffer
     */
    public void release(final ByteBuffer buffer)
    {
        if (buffer == null)
        {
            return;
        }
        if (clearOnRelease)
        {
            bufferWrapper.wrap(buffer);
            bufferWrapper.setMemory(0, buffer.capacity(), ZERO);
        }
        buffer.clear();
        final int size = buffer.capacity();
        validateBufferSize(size);
        final Deque<ByteBuffer> bufferPool = bufferPool(size);
        bufferPool.addLast(buffer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        for (int i = 0; i < POOL_COUNT; i++)
        {
            buffersBySize[i] = null;
        }
    }

    private Deque<ByteBuffer> bufferPool(final int size)
    {
        return buffersBySize[poolIndex(size)];
    }

    private static int poolIndex(final int size)
    {
        return Integer.numberOfTrailingZeros(size);
    }

    private static void validateBufferSize(final int size)
    {
        if (Integer.bitCount(size) != 1)
        {
            throw new IllegalArgumentException("Can only pool buffers that are a power-of-two in size");
        }
        if (size == Integer.MAX_VALUE)
        {
            throw new IllegalArgumentException("Can only pool buffers up to " + (1 << 30) + " in size");
        }
    }
}
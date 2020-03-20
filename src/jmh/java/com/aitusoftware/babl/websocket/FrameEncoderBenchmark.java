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
package com.aitusoftware.babl.websocket;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import com.aitusoftware.babl.monitoring.NoOpSessionContainerStatistics;
import com.aitusoftware.babl.monitoring.NoOpSessionStatistics;
import com.aitusoftware.babl.pool.BufferPool;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.config.SessionConfig;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(2)
@Warmup(iterations = 7, time = 1)
@Measurement(iterations = 7, time = 1)
public class FrameEncoderBenchmark
{
    private static final int SOURCE_CAPACITY = 1024;
    private final BufferPool bufferPool = new BufferPool();
    private final FrameEncoder frameEncoder = new FrameEncoder(
        bufferPool, new NoOpSessionDataListener(), new SessionConfig().maxWebSocketFrameLength(120),
        false, new NoOpSessionContainerStatistics());
    private final MutableDirectBuffer payload = new UnsafeBuffer(ByteBuffer.allocateDirect(SOURCE_CAPACITY));

    @Setup
    public void setup()
    {
        frameEncoder.init(new NoOpSessionStatistics(), 17L);
        for (int i = 0; i < SOURCE_CAPACITY / 8; i++)
        {
            payload.putLong(i * 8, System.nanoTime());
        }
    }

    @Benchmark
    public long encodeSingleFrame()
    {
        final int result = frameEncoder.encodeMessage(ContentType.BINARY, payload, 0, 100);
        final int encodedLength = frameEncoder.sendBuffer().position();
        frameEncoder.sendBuffer().clear();
        return result + encodedLength;
    }

    @Benchmark
    public long encodeMultipleFrames()
    {
        final int result = frameEncoder.encodeMessage(ContentType.BINARY, payload, 0, 500);
        final int encodedLength = frameEncoder.sendBuffer().position();
        frameEncoder.sendBuffer().clear();
        return result + encodedLength;
    }
}
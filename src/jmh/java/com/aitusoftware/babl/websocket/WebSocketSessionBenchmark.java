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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.TimeUnit;

import com.aitusoftware.babl.config.SessionConfig;
import com.aitusoftware.babl.monitoring.NoOpSessionContainerStatistics;
import com.aitusoftware.babl.monitoring.NoOpSessionStatistics;
import com.aitusoftware.babl.pool.BufferPool;
import com.aitusoftware.babl.time.SingleThreadedCachedClock;
import com.aitusoftware.babl.user.Application;
import com.aitusoftware.babl.user.ContentType;

import org.agrona.DirectBuffer;
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
public class WebSocketSessionBenchmark
{
    private static final NoOpSessionDataListener SESSION_DATA_LISTENER = new NoOpSessionDataListener();
    private static final SessionConfig SESSION_CONFIG = new SessionConfig();
    private static final NoOpSessionContainerStatistics SESSION_CONTAINER_STATISTICS =
        new NoOpSessionContainerStatistics();
    private static final NoOpSessionStatistics SESSION_STATISTICS = new NoOpSessionStatistics();
    private static final SingleThreadedCachedClock CLOCK = new SingleThreadedCachedClock();
    private static final byte[] PAYLOAD = new byte[100];
    private static final int TOTAL_FRAME_LENGTH = PAYLOAD.length + 6;

    private final BufferPool bufferPool = new BufferPool();
    private final FrameEncoder frameEncoder = new FrameEncoder(
        bufferPool, SESSION_DATA_LISTENER, SESSION_CONFIG, SESSION_CONTAINER_STATISTICS);
    private final PingAgent pingAgent = new PingAgent(frameEncoder, CLOCK, 0L, 0L, SESSION_DATA_LISTENER);
    private final CaptureApplication application = new CaptureApplication();
    private final MessageDispatcher messageDispatcher = new MessageDispatcher(application);
    private final FrameDecoder frameDecoder = new FrameDecoder(
        messageDispatcher, SESSION_CONFIG, bufferPool, pingAgent, false, SESSION_CONTAINER_STATISTICS);
    private final ByteBuffer singleFramePayload = ByteBuffer.allocateDirect(106);
    private final WebSocketSession session = new WebSocketSession(SESSION_DATA_LISTENER, frameDecoder, frameEncoder,
        SESSION_CONFIG, bufferPool, application, pingAgent, SESSION_STATISTICS, SESSION_CONTAINER_STATISTICS);
    private final DataSource dataSource = new DataSource();
    private final DataSink dataSink = new DataSink();

    @Setup
    public void setup()
    {
        CLOCK.set(System.currentTimeMillis());
        frameDecoder.init(SESSION_STATISTICS, 0L);
        MsgUtil.writeWebSocketFrame(PAYLOAD, PAYLOAD.length, singleFramePayload,
            0, 0, true, Constants.OPCODE_BINARY);
        singleFramePayload.clear();

        session.init(7L, new StubConnectionUpgrade(), cu -> {}, System.currentTimeMillis(),
            dataSource, dataSink);
        session.validated();
    }

    @Benchmark
    public long processSingleFrame() throws IOException
    {
        session.doSendWork();
        session.doReceiveWork();
        return application.messageCount + dataSink.totalBytes + dataSink.totalBytes;
    }

    private static final class DataSink implements WritableByteChannel
    {
        private long totalBytes = 0;

        @Override
        public int write(final ByteBuffer src)
        {
            final int remaining = src.remaining();
            src.position(src.limit());
            totalBytes += remaining;
            return remaining;
        }

        @Override
        public boolean isOpen()
        {
            return true;
        }

        @Override
        public void close()
        {
        }
    }

    private final class DataSource implements ReadableByteChannel
    {
        private long totalBytes = 0;

        @Override
        public int read(final ByteBuffer dst)
        {
            if (dst.remaining() >= TOTAL_FRAME_LENGTH)
            {
                singleFramePayload.limit(TOTAL_FRAME_LENGTH).position(0);
                dst.put(singleFramePayload);
                totalBytes += TOTAL_FRAME_LENGTH;
                return TOTAL_FRAME_LENGTH;
            }
            return 0;
        }

        @Override
        public boolean isOpen()
        {
            return true;
        }

        @Override
        public void close()
        {
        }
    }

    private static class CaptureApplication implements Application
    {
        private long messageCount;

        @Override
        public int onSessionConnected(final Session session)
        {
            return SendResult.OK;
        }

        @Override
        public int onSessionDisconnected(
            final Session session,
            final DisconnectReason reason)
        {
            return SendResult.OK;
        }

        @Override
        public int onSessionMessage(
            final Session session,
            final ContentType contentType,
            final DirectBuffer msg,
            final int offset,
            final int length)
        {
            messageCount++;
            session.send(contentType, msg, 0, length);
            return SendResult.OK;
        }
    }

    private static final class StubConnectionUpgrade extends ConnectionUpgrade
    {
        StubConnectionUpgrade()
        {
            super(null, null, null);
        }

        @Override
        boolean handleUpgrade(final ByteBuffer input, final ByteBuffer output)
        {
            return true;
        }
    }
}
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
public class FrameDecoderBenchmark
{
    private final CaptureMessageReceiver messageReceiver = new CaptureMessageReceiver();
    private final BufferPool bufferPool = new BufferPool();
    private final PingAgent pingAgent = new PingAgent(null, null, 0L, 0L, new NoOpSessionDataListener());
    private final FrameDecoder frameDecoder = new FrameDecoder(
        messageReceiver, new SessionConfig(), bufferPool, pingAgent, false, new NoOpSessionContainerStatistics());
    private final ByteBuffer singleFramePayload = ByteBuffer.allocateDirect(106);
    private final ByteBuffer multipleFramePayload = ByteBuffer.allocateDirect(1024);

    @Setup
    public void setup()
    {
        frameDecoder.init(new NoOpSessionStatistics());
        writeWebSocketFrame(new byte[100], 100, singleFramePayload,
            0, 0, true, Constants.OPCODE_BINARY);
        singleFramePayload.clear();

        writeWebSocketFrame(new byte[100], 100, multipleFramePayload,
            0, 0, false, Constants.OPCODE_BINARY);
        writeWebSocketFrame(new byte[100], 100, multipleFramePayload,
            0, 0, false, Constants.OPCODE_CONTINUATION);
        writeWebSocketFrame(new byte[100], 100, multipleFramePayload,
            0, 0, false, Constants.OPCODE_CONTINUATION);
        writeWebSocketFrame(new byte[100], 100, multipleFramePayload,
            0, 0, true, Constants.OPCODE_CONTINUATION);
        multipleFramePayload.clear();
    }

    @Benchmark
    public long decodeSingleFrame()
    {
        frameDecoder.decode(singleFramePayload, null);

        return messageReceiver.messageCount;
    }

    @Benchmark
    public long decodeMultipleFrames()
    {
        int read;
        read = frameDecoder.decode(multipleFramePayload, null);
        multipleFramePayload.position(multipleFramePayload.position() + read);
        read = frameDecoder.decode(multipleFramePayload, null);
        multipleFramePayload.position(multipleFramePayload.position() + read);
        read = frameDecoder.decode(multipleFramePayload, null);
        multipleFramePayload.position(multipleFramePayload.position() + read);
        read = frameDecoder.decode(multipleFramePayload, null);
        multipleFramePayload.position(multipleFramePayload.position() + read);

        return messageReceiver.messageCount;
    }

    private static class CaptureMessageReceiver implements MessageReceiver
    {
        private long messageCount = 0L;

        @Override
        public int onMessage(
            final WebSocketSession session, final ContentType contentType,
            final DirectBuffer buffer, final int offset, final int length)
        {
            messageCount++;
            return SendResult.OK;
        }

        @Override
        public void onCloseMessage(final short closeReason, final WebSocketSession session)
        {

        }
    }

    private static void writeWebSocketFrame(
        final byte[] payload, final int lengthToWrite,
        final ByteBuffer dst, final int maskingKey,
        final int maskingKeyOffset, final boolean isFin,
        final int opCode)
    {
        dst.order(Constants.NETWORK_BYTE_ORDER);
        final int startPosition = dst.position();
        int relativeOffset = startPosition;
        final int remaining = dst.remaining();
        final int headerLength = EncodingUtil.requestHeaderLengthByPayloadLength(payload.length);

        if (lengthToWrite > remaining - headerLength)
        {
            throw new IllegalStateException("Message too large for buffer");
        }
        if (isFin)
        {
            dst.put(relativeOffset, (byte)(0b1000_0000 | opCode));
        }
        else
        {
            dst.put(relativeOffset, (byte)opCode);
        }
        relativeOffset++;
        if (EncodingUtil.isSmallRequestMessage(headerLength))
        {
            dst.put(relativeOffset, (byte)(0b1000_0000 | payload.length));
            relativeOffset++;
        }
        else if (EncodingUtil.isMediumRequestMessage(headerLength))
        {
            dst.put(relativeOffset, (byte)(0b1000_0000 | 126));
            relativeOffset++;
            dst.putShort(relativeOffset, (short)payload.length);
            relativeOffset += 2;
        }
        else
        {
            dst.put(relativeOffset, (byte)(0b1000_0000 | 127));
            relativeOffset++;
            dst.putLong(relativeOffset, payload.length);
            relativeOffset += 8;
        }
        dst.putInt(relativeOffset, maskingKey);
        relativeOffset += 4;
        dst.position(relativeOffset);
        int maskingKeyIndex = maskingKeyOffset;
        final byte[] maskingKeyBytes = new byte[4];
        maskingKeyBytes[0] = (byte)(maskingKey >> 24);
        maskingKeyBytes[1] = (byte)(maskingKey >> 16);
        maskingKeyBytes[2] = (byte)(maskingKey >> 8);
        maskingKeyBytes[3] = (byte)(maskingKey);
        for (int i = 0; i < lengthToWrite; i++)
        {
            dst.put((byte)(payload[i] ^ maskingKeyBytes[maskingKeyIndex & 3]));
            maskingKeyIndex++;
        }
        dst.position(startPosition + headerLength + lengthToWrite);
    }

}
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

import static com.google.common.truth.Truth.assertThat;

import java.nio.ByteBuffer;

import com.aitusoftware.babl.monitoring.NoOpSessionContainerStatistics;
import com.aitusoftware.babl.monitoring.NoOpSessionStatistics;
import com.aitusoftware.babl.pool.BufferPool;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.config.SessionConfig;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FrameEncoderTest
{
    private static final int LARGE_PAYLOAD_LENGTH = 20000;
    private static final int SMALL_PAYLOAD_LENGTH = 500;
    private static final int MAX_FRAME_SIZE = 8192;
    private static final int SEND_BUFFER_MAX_SIZE = 65536;
    private final FrameEncoder frameEncoder = new FrameEncoder(new BufferPool(),
        new NoOpSessionDataListener(), new SessionConfig().maxWebSocketFrameLength(MAX_FRAME_SIZE)
        .sendBufferSize(1024)
        .maxBufferSize(SEND_BUFFER_MAX_SIZE), new NoOpSessionContainerStatistics());
    private ByteBuffer dst;

    @BeforeEach
    void setUp()
    {
        frameEncoder.init(new NoOpSessionStatistics(), 0L);
    }

    @Test
    void shouldFragmentMessagesIntoMaxFrameSize()
    {
        writeFrame(LARGE_PAYLOAD_LENGTH);
        dst = frameEncoder.sendBuffer();
        dst.flip();
        final int headerLength = EncodingUtil.responseHeaderLengthByPayloadLength(MAX_FRAME_SIZE);
        FrameUtil.assertWebSocketFrame(dst, 0,
            MAX_FRAME_SIZE - headerLength, Constants.OPCODE_BINARY, false);
        FrameUtil.assertWebSocketFrame(dst, MAX_FRAME_SIZE,
            MAX_FRAME_SIZE - headerLength, Constants.OPCODE_CONTINUATION, false);
        FrameUtil.assertWebSocketFrame(dst, 2 * MAX_FRAME_SIZE,
            (LARGE_PAYLOAD_LENGTH - (2 * (MAX_FRAME_SIZE - headerLength))),
            Constants.OPCODE_CONTINUATION, true);
    }

    @Test
    void shouldEncodeMultipleSmallFrames()
    {
        writeFrame(SMALL_PAYLOAD_LENGTH);
        writeFrame(SMALL_PAYLOAD_LENGTH);
        writeFrame(SMALL_PAYLOAD_LENGTH);
        writeFrame(SMALL_PAYLOAD_LENGTH);
        dst = frameEncoder.sendBuffer();
        dst.flip();
        final int headerLength = EncodingUtil.responseHeaderLengthByPayloadLength(SMALL_PAYLOAD_LENGTH);
        final int offset = 0;
        assertCompleteFrameAt(offset);
        assertCompleteFrameAt(SMALL_PAYLOAD_LENGTH + headerLength);
        assertCompleteFrameAt(2 * (SMALL_PAYLOAD_LENGTH + headerLength));
        assertCompleteFrameAt(3 * (SMALL_PAYLOAD_LENGTH + headerLength));
    }

    @Test
    void shouldIndicateBackPressureWhenPayloadWouldExceedMaxAvailableBufferSpace()
    {
        writeFrame(LARGE_PAYLOAD_LENGTH);
        writeFrame(LARGE_PAYLOAD_LENGTH);
        writeFrame(LARGE_PAYLOAD_LENGTH);
        writeFrame(LARGE_PAYLOAD_LENGTH, SendResult.BACK_PRESSURE);
    }

    @Test
    void shouldFailWhenPayloadWouldExceedMaxConfiguredBufferSize()
    {
        writeFrame(SEND_BUFFER_MAX_SIZE, SendResult.INVALID_MESSAGE);
    }

    private void assertCompleteFrameAt(final int offset)
    {
        FrameUtil.assertWebSocketFrame(dst, offset, SMALL_PAYLOAD_LENGTH, Constants.OPCODE_BINARY, true);
    }

    private void writeFrame(final int payloadLength)
    {
        writeFrame(payloadLength, SendResult.OK);
    }

    private void writeFrame(final int payloadLength, final int expectedResult)
    {
        final int sendResult = frameEncoder.encodeMessage(ContentType.BINARY,
            new UnsafeBuffer(new byte[payloadLength]), 0, payloadLength);
        assertThat(sendResult).isEqualTo(expectedResult);
    }
}
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.monitoring.NoOpSessionContainerStatistics;
import com.aitusoftware.babl.monitoring.NoOpSessionStatistics;
import com.aitusoftware.babl.pool.BufferPool;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.config.SessionConfig;

import org.agrona.DirectBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FrameDecoderTest
{
    private static final int MASKING_KEY = 0x21212121;
    private static final byte DATUM = (byte)'a';
    private static final WebSocketSession DUMMY_SESSION = null;
    private static final int PAYLOAD_LENGTH = 100;
    private static final int FRAME_LENGTH =
        PAYLOAD_LENGTH + EncodingUtil.requestHeaderLengthByPayloadLength(PAYLOAD_LENGTH);
    private final PingAgent pingAgent = new PingAgent(null, null, 0, 0, new NoOpSessionDataListener());
    private final CaptureMessageReceiver captureMessageReceiver = new CaptureMessageReceiver();
    private final SessionConfig sessionConfig = new SessionConfig()
        .sessionDecodeBufferSize(1024).maxSessionDecodeBufferSize(2048);
    private final FrameDecoder frameDecoder = new FrameDecoder(captureMessageReceiver,
        sessionConfig, new BufferPool(), pingAgent, true,
        new NoOpSessionContainerStatistics());
    private final byte[] payload = new byte[PAYLOAD_LENGTH];
    private final ByteBuffer sourceBuffer = ByteBuffer.allocateDirect(128);

    @BeforeAll
    static void enableLogging()
    {
        System.setProperty(Logger.DEBUG_ENABLED_PROPERTY, "true");
    }

    @BeforeEach
    void setUp()
    {
        frameDecoder.init(new NoOpSessionStatistics());
        Arrays.fill(payload, DATUM);
    }

    /*
     * state: init
     * receive: complete message, fully read
     * action: decode in place, pass to handler function
     * post: advance buffer position to end of message
     */
    @Test
    void shouldImmediatelyDeliverFullyReadCompleteMessageGivenNoApplicationBackPressure()
    {
        FrameUtil.writeWebSocketFrame(payload, payload.length,
            sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        assertThat(frameDecoder.decode(sourceBuffer, DUMMY_SESSION)).isEqualTo(FRAME_LENGTH);
        assertThat(frameDecoder.hasQueuedMessage()).isFalse();

        assertThat(captureMessageReceiver.messageCount).isEqualTo(1);
        assertThat(captureMessageReceiver.lastMessage.length).isEqualTo(PAYLOAD_LENGTH);
        for (int i = 0; i < captureMessageReceiver.lastMessage.length; i++)
        {
            assertThat(captureMessageReceiver.lastMessage[i]).named("Failed at " + i).isEqualTo(DATUM);
        }
    }

    /*
     * state: init
     * receive: complete message, fully read
     * action: decode in place, pass to handler function
     * post: advance buffer position to end of message
     */
    @Test
    void shouldImmediatelyDeliverFullyReadSingleByteCompleteMessageGivenNoApplicationBackPressure()
    {
        final byte msg = (byte)7;
        FrameUtil.writeWebSocketFrame(new byte[] {msg}, 1,
            sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        assertThat(frameDecoder.decode(sourceBuffer, DUMMY_SESSION)).isEqualTo(7);
        assertThat(frameDecoder.hasQueuedMessage()).isFalse();

        assertThat(captureMessageReceiver.messageCount).isEqualTo(1);
        assertThat(captureMessageReceiver.lastMessage.length).isEqualTo(1);
        for (int i = 0; i < captureMessageReceiver.lastMessage.length; i++)
        {
            assertThat(captureMessageReceiver.lastMessage[i]).named("Failed at " + i).isEqualTo(msg);
        }
    }

    /*
     * state: init
     * receive: complete message, fully read
     * action: decode in place, pass to handler function <- if not delivered, buffer in dstBuffer
     * post: advance buffer position to end of message
     */
    @Test
    void shouldBufferFullyReadCompleteMessageGivenApplicationBackPressure()
    {
        captureMessageReceiver.stubbedSendResult = SendResult.BACK_PRESSURE;
        FrameUtil.writeWebSocketFrame(payload, payload.length,
            sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        assertThat(frameDecoder.decode(sourceBuffer, DUMMY_SESSION)).isEqualTo(FRAME_LENGTH);

        assertThat(captureMessageReceiver.messageCount).isEqualTo(0);
        assertThat(frameDecoder.hasQueuedMessage()).isTrue();
    }

    /*
     * state: init
     * receive: complete message, fully read
     * action: decode in place, pass to handler function <- if not delivered, buffer in dstBuffer
     * post: advance buffer position to end of message

     * state: complete message queued
     * receive: anything
     * pre-action: try to deliver to handler function and reset dstBuffer <- if not delivered, leave in dstBuffer
     * action: if delivered, try normal action
     *         else return without altering buffer positions
     */
    @Test
    void shouldDeliverBufferedCompleteMessageOnNextInvocation()
    {
        captureMessageReceiver.stubbedSendResult = SendResult.BACK_PRESSURE;
        FrameUtil.writeWebSocketFrame(payload, PAYLOAD_LENGTH,
            sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        assertThat(frameDecoder.decode(sourceBuffer, DUMMY_SESSION)).isEqualTo(FRAME_LENGTH);

        assertThat(captureMessageReceiver.messageCount).isEqualTo(0);
        assertThat(frameDecoder.hasQueuedMessage()).isTrue();

        captureMessageReceiver.stubbedSendResult = SendResult.OK;

        frameDecoder.decode(ByteBuffer.allocateDirect(8), DUMMY_SESSION);
        assertThat(frameDecoder.hasQueuedMessage()).isFalse();

        assertThat(captureMessageReceiver.messageCount).isEqualTo(1);
        assertThat(captureMessageReceiver.lastMessage.length).isEqualTo(PAYLOAD_LENGTH);
        for (int i = 0; i < captureMessageReceiver.lastMessage.length; i++)
        {
            assertThat(captureMessageReceiver.lastMessage[i]).named("Failed at " + i).isEqualTo(DATUM);
        }
    }

    /*
     * state: complete message buffered
     * receive: complete message, fully read
     * action: decode in place, pass to handler function <- if not delivered, buffer in dstBuffer
     * post: advance buffer position to end of message
     */
    @Test
    void shouldDeliverBufferedCompleteMessageAndNextMessageOnNextInvocation()
    {
        captureMessageReceiver.stubbedSendResult = SendResult.BACK_PRESSURE;
        FrameUtil.writeWebSocketFrame(payload, PAYLOAD_LENGTH,
            sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        assertThat(frameDecoder.decode(sourceBuffer, DUMMY_SESSION)).isEqualTo(FRAME_LENGTH);

        assertThat(captureMessageReceiver.messageCount).isEqualTo(0);
        assertThat(frameDecoder.hasQueuedMessage()).isTrue();

        captureMessageReceiver.stubbedSendResult = SendResult.OK;

        sourceBuffer.clear();
        FrameUtil.writeWebSocketFrame(payload, PAYLOAD_LENGTH,
            sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        assertThat(frameDecoder.decode(sourceBuffer, DUMMY_SESSION)).isEqualTo(FRAME_LENGTH);
        assertThat(frameDecoder.hasQueuedMessage()).isFalse();
        assertThat(captureMessageReceiver.messageCount).isEqualTo(2);
        assertReceivedMessageContent(captureMessageReceiver.receivedMessages.get(0));
        assertReceivedMessageContent(captureMessageReceiver.receivedMessages.get(1));
    }

    /* state: init
     * receive: complete message, partially read
     * action: leave in buffer, return NEED_MORE_DATA
     * post: leave buffer position
     */
    @Test
    void shouldDoNothingWhenInsufficientDataForCompleteMessage()
    {
        final int partialPayload = PAYLOAD_LENGTH / 2;
        FrameUtil.writeWebSocketFrame(payload, partialPayload,
            sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        final int bytesAvailable = sourceBuffer.remaining();
        assertThat(frameDecoder.decode(sourceBuffer, DUMMY_SESSION)).isEqualTo(SendResult.NEED_MORE_DATA);
        assertThat(frameDecoder.hasQueuedMessage()).isFalse();
        assertThat(captureMessageReceiver.messageCount).isEqualTo(0);
        assertThat(sourceBuffer.position()).isEqualTo(0);
        assertThat(sourceBuffer.remaining()).isEqualTo(bytesAvailable);
    }

    /* state: init
     * receive: complete message, partially read
     * action: leave in buffer, return NEED_MORE_DATA
     * post: leave buffer position
     */
    @Test
    void shouldDoNothingWhenInsufficientDataForSmallMessageHeader()
    {
        final int partialPayload = PAYLOAD_LENGTH / 2;
        FrameUtil.writeWebSocketFrame(payload, partialPayload,
            sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.limit(1);
        sourceBuffer.flip();
        final int bytesAvailable = sourceBuffer.remaining();
        assertThat(frameDecoder.decode(sourceBuffer, DUMMY_SESSION)).isEqualTo(SendResult.NEED_MORE_DATA);
        assertThat(frameDecoder.hasQueuedMessage()).isFalse();
        assertThat(captureMessageReceiver.messageCount).isEqualTo(0);
        assertThat(sourceBuffer.position()).isEqualTo(0);
        assertThat(sourceBuffer.remaining()).isEqualTo(bytesAvailable);
    }

    /* state: init
     * receive: complete message, header partially read
     * action: leave in buffer, return NEED_MORE_DATA
     * post: leave buffer position
     */
    @Test
    void shouldDoNothingWhenInsufficientDataForMediumPayloadHeader()
    {
        final int partialPayload = 30000;
        final ByteBuffer tmpBuffer = ByteBuffer.allocate(60000);
        FrameUtil.writeWebSocketFrame(new byte[60000], partialPayload,
            tmpBuffer, MASKING_KEY, 0);
        tmpBuffer.position(2);
        tmpBuffer.flip();
        final int bytesAvailable = tmpBuffer.remaining();
        final ByteBuffer incomplete = ByteBuffer.allocate(bytesAvailable);
        incomplete.put(tmpBuffer);
        incomplete.clear();
        assertThat(frameDecoder.decode(incomplete, DUMMY_SESSION)).isEqualTo(SendResult.NEED_MORE_DATA);
        assertThat(frameDecoder.hasQueuedMessage()).isFalse();
        assertThat(captureMessageReceiver.messageCount).isEqualTo(0);
        assertThat(incomplete.position()).isEqualTo(0);
        assertThat(incomplete.remaining()).isEqualTo(bytesAvailable);
    }

    /*
     * state: init
     * receive: partial message, fully read
     * action: decode and add to dstBuffer
     * post: advance buffer position to end of message
     */
    @Test
    void shouldBufferPartialMessageConsumingBuffer()
    {
        FrameUtil.writeContinuationWebSocketFrame(payload, PAYLOAD_LENGTH,
            sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        assertThat(frameDecoder.decode(sourceBuffer, DUMMY_SESSION)).isEqualTo(FRAME_LENGTH);
        assertThat(frameDecoder.hasQueuedMessage()).isFalse();

        assertThat(captureMessageReceiver.messageCount).isEqualTo(0);
    }

    /*
     * state: init
     * receive: partial message, fully read
     * action: decode and add to dstBuffer
     * post: advance buffer position to end of message

     * state: partial message accumulating
     * receive: complete message
     * action: decode and add to dstBuffer, delivery to handler function <- if not delivered, leave in dstBuffer
     * post: advance buffer position to end of message
     */
    @Test
    void shouldBufferPartialMessageConsumingBufferAndDeliverCompleteMessageOnFinFrame()
    {
        FrameUtil.writeContinuationWebSocketFrame(payload, PAYLOAD_LENGTH,
            sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        assertThat(frameDecoder.decode(sourceBuffer, DUMMY_SESSION)).isEqualTo(FRAME_LENGTH);
        sourceBuffer.clear();
        FrameUtil.writeWebSocketFrame(payload, PAYLOAD_LENGTH,
            sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        assertThat(frameDecoder.decode(sourceBuffer, DUMMY_SESSION)).isEqualTo(FRAME_LENGTH);
        assertThat(frameDecoder.hasQueuedMessage()).isFalse();
        assertThat(captureMessageReceiver.messageCount).isEqualTo(1);
        assertThat(captureMessageReceiver.lastMessage.length).isEqualTo(payload.length * 2);
        for (int i = 0; i < captureMessageReceiver.lastMessage.length; i++)
        {
            assertThat(captureMessageReceiver.lastMessage[i]).named("Failed at " + i).isEqualTo(DATUM);
        }
    }

    /*
     * state: init
     * receive: partial message, partially read
     * action: leave in buffer, return NEED_MORE_DATA
     * post: leave buffer position
     */
    @Test
    void shouldDoNothingWhenInsufficientDataForPartialMessage()
    {
        final int partialPayloadLength = 50;
        FrameUtil.writeContinuationWebSocketFrame(payload, partialPayloadLength,
            sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        final int bytesRemaining = sourceBuffer.remaining();
        assertThat(frameDecoder.decode(sourceBuffer, DUMMY_SESSION)).isEqualTo(SendResult.NEED_MORE_DATA);
        assertThat(frameDecoder.hasQueuedMessage()).isFalse();
        assertThat(captureMessageReceiver.messageCount).isEqualTo(0);
        assertThat(sourceBuffer.position()).isEqualTo(0);
        assertThat(sourceBuffer.remaining()).isEqualTo(bytesRemaining);
    }

    @Test
    void shouldNotDeliverControlFrameToApplication()
    {
        FrameUtil.writePingFrame(payload, payload.length, sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        frameDecoder.decode(sourceBuffer, DUMMY_SESSION);
        assertThat(captureMessageReceiver.messageCount).isEqualTo(0);
        assertThat(pingAgent.pongRequired()).isTrue();
    }

    @Test
    void shouldNotDeliverInternallyBufferedControlFrameToApplication()
    {
        captureMessageReceiver.stubbedSendResult = SendResult.BACK_PRESSURE;
        FrameUtil.writePingFrame(payload, payload.length, sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        frameDecoder.decode(sourceBuffer, DUMMY_SESSION);
        sourceBuffer.clear();
        frameDecoder.decode(sourceBuffer, DUMMY_SESSION);
        assertThat(captureMessageReceiver.messageCount).isEqualTo(0);
        assertThat(pingAgent.pongRequired()).isTrue();
    }

    @Test
    void shouldPassThroughDecodedSourceBufferIfCompleteMessageIsAvailable()
    {
        FrameUtil.writeWebSocketFrame(payload, payload.length, sourceBuffer, MASKING_KEY, 0);
        sourceBuffer.flip();
        frameDecoder.decode(sourceBuffer, DUMMY_SESSION);

        assertThat(captureMessageReceiver.messageCount).isEqualTo(1);
        assertThat(captureMessageReceiver.lastMessage.length).isEqualTo(payload.length);
        for (int i = 0; i < captureMessageReceiver.lastMessage.length; i++)
        {
            assertThat(captureMessageReceiver.lastMessage[i]).named("Failed at " + i).isEqualTo(DATUM);
        }
        assertThat(captureMessageReceiver.sourceBuffer).isSameAs(sourceBuffer);
    }

    private static class CaptureMessageReceiver implements MessageReceiver
    {
        private int stubbedSendResult = SendResult.OK;
        private int messageCount;
        private byte[] lastMessage;
        private List<byte[]> receivedMessages = new ArrayList<>();
        private ByteBuffer sourceBuffer;

        @Override
        public int onMessage(
            final WebSocketSession session, final ContentType contentType,
            final DirectBuffer buffer,
            final int offset,
            final int length)
        {
            if (stubbedSendResult == SendResult.OK)
            {
                messageCount++;
                lastMessage = new byte[length];
                buffer.getBytes(offset, lastMessage, 0, length);
                sourceBuffer = buffer.byteBuffer();
                receivedMessages.add(lastMessage);
            }
            return stubbedSendResult;
        }

        @Override
        public void onCloseMessage(final short closeReason, final WebSocketSession session)
        {

        }
    }

    private void assertReceivedMessageContent(final byte[] receivedMessage)
    {
        assertThat(receivedMessage.length).isEqualTo(payload.length);
        for (int i = 0; i < receivedMessage.length; i++)
        {
            assertThat(receivedMessage[i]).named("Failed at " + i).isEqualTo(DATUM);
        }
    }
}
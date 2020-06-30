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

import static com.aitusoftware.babl.websocket.Constants.NETWORK_BYTE_ORDER;

import java.nio.ByteBuffer;

import com.aitusoftware.babl.config.SessionConfig;
import com.aitusoftware.babl.log.Category;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.monitoring.SessionContainerStatistics;
import com.aitusoftware.babl.monitoring.SessionStatistics;
import com.aitusoftware.babl.pool.BufferPool;
import com.aitusoftware.babl.user.ContentType;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

final class FrameDecoder
{
    private static final int MAX_PAYLOAD_SIZE = Integer.MAX_VALUE / 2;
    private static final int MASK_COPY_CHUNK_SIZE_BYTES = 8;
    private static final int CHUNK_MASK = MASK_COPY_CHUNK_SIZE_BYTES - 1;
    private static final int CHUNK_SHIFT = 3;
    private static final int NO_OP_CODE = -1;
    private static final int MINIMUM_PAYLOAD_SIZE = 2;

    private final UnsafeBuffer srcBuffer = new UnsafeBuffer();
    private final UnsafeBuffer dstBuffer = new UnsafeBuffer();
    private final MessageReceiver messageReceiver;
    private final FrameHeader frameHeader = new FrameHeader();
    private final SessionConfig sessionConfig;
    private final BufferPool bufferPool;
    private final PingAgent pingAgent;
    private final SessionContainerStatistics sessionContainerStatistics;
    private final int internalBufferMaxCapacity;
    private final boolean requiresMasking;
    private final byte[] maskingKeyBytes = new byte[4];
    private SessionStatistics sessionStatistics;
    private long maskingKey;
    private int dstOffset;
    private int payloadLength;
    private int frameLength;
    private int maskingKeyIndex = 0;
    private int initialFrameOpCode = NO_OP_CODE;
    private boolean hasQueuedMessage;
    private long sessionId;

    FrameDecoder(
        final MessageReceiver messageReceiver,
        final SessionConfig sessionConfig,
        final BufferPool bufferPool,
        final PingAgent pingAgent,
        final boolean requiresMasking,
        final SessionContainerStatistics sessionContainerStatistics)
    {
        this.messageReceiver = messageReceiver;
        this.sessionConfig = sessionConfig;
        this.bufferPool = bufferPool;
        this.pingAgent = pingAgent;
        this.requiresMasking = requiresMasking;
        this.sessionContainerStatistics = sessionContainerStatistics;
        dstBuffer.wrap(bufferPool.acquire(sessionConfig.sessionDecodeBufferSize()));
        this.internalBufferMaxCapacity = sessionConfig.maxSessionDecodeBufferSize();
    }

    int decode(final ByteBuffer payload, final WebSocketSession session)
    {
        if (hasQueuedMessage)
        {
            final int deliveryResult = publishToApplication(dstBuffer, 0, dstOffset, session);
            if (SendResult.OK == deliveryResult)
            {
                resetAfterMessageDelivery();
            }
            else
            {
                return SendResult.BACK_PRESSURE;
            }
        }
        if (payload.remaining() < MINIMUM_PAYLOAD_SIZE)
        {
            return SendResult.NEED_MORE_DATA;
        }
        srcBuffer.wrap(payload, payload.position(), payload.remaining());

        final byte flags = srcBuffer.getByte(0);
        final byte payloadLengthIndicator = srcBuffer.getByte(1);
        frameHeader.set(flags, payloadLengthIndicator);
        if (!frameHeader.isMasked && requiresMasking)
        {
            return SendResult.INVALID_MESSAGE;
        }
        if (!headerDataAvailable(payload, payloadLengthIndicator))
        {
            return SendResult.NEED_MORE_DATA;
        }
        final int payloadOffset = frameHeader.payloadOffset;
        final int payloadLength = assignPayloadLength();
        if (payloadLength < 0 || payloadLength + dstOffset > internalBufferMaxCapacity)
        {
            resetAfterMessageDelivery();
            return SendResult.INVALID_MESSAGE;
        }
        if (payloadLength > payload.remaining() - payloadOffset)
        {
            return SendResult.NEED_MORE_DATA;
        }
        return decodeFrame(payload, session, payloadOffset, payloadLength);
    }

    private int decodeFrame(
        final ByteBuffer payload,
        final WebSocketSession session,
        final int payloadOffset,
        final int payloadLength)
    {
        Logger.log(Category.DECODE, "Received new message (%d); data available: %d%n",
            frameHeader.opCode, payload.remaining());
        Logger.log(Category.DECODE, "Payload length: %d%n", payloadLength);

        assignMaskingKey();

        final int sendResult;
        if (dstOffset == 0)
        {
            initialFrameOpCode = frameHeader.opCode;
        }
        switch (frameHeader.opCode)
        {
            case Constants.OPCODE_CONTINUATION:
            case Constants.OPCODE_BINARY:
            case Constants.OPCODE_TEXT:
            case Constants.OPCODE_PING:
            case Constants.OPCODE_PONG:
            case Constants.OPCODE_CLOSE:
                srcBuffer.wrap(payload, payload.position() + payloadOffset, payloadLength);
                sendResult = handleFrame(srcBuffer, payloadLength, session);
                break;
            default:
                sessionContainerStatistics.invalidOpCode();
                sendResult = SendResult.INVALID_MESSAGE;
        }

        return sendResult;
    }

    private int handleFrame(final MutableDirectBuffer payload, final int payloadLength, final WebSocketSession session)
    {
        if (isSingleCompleteMessage())
        {
            Logger.log(Category.DECODE, "Received complete frame of %dB%n", payloadLength);

            final int bytesConsumed = frameLength;
            if (frameHeader.opCode == Constants.OPCODE_PING)
            {
                handlePing(payload, payloadLength, session);
            }
            else if (frameHeader.opCode == Constants.OPCODE_PONG)
            {
                handlePong(payload, payloadLength, session);
            }
            else if (frameHeader.opCode == Constants.OPCODE_CLOSE)
            {
                handleClose(payload, payloadLength, session);
            }
            else
            {
                sessionStatistics.frameDecoded();
                return deliverDirect(payload, 0, payloadLength, session);
            }
            sessionStatistics.frameDecoded();
            resetAfterMessageDelivery();
            return bytesConsumed;
        }
        else
        {
            return enqueueFrame(payload, payloadLength, session);
        }
    }

    private int enqueueFrame(final MutableDirectBuffer payload, final int payloadLength, final WebSocketSession session)
    {
        Logger.log(Category.DECODE, "Copying %dB to %d%n", payloadLength, dstOffset + payloadLength);
        if (internalBufferIncreaseRequired(payloadLength))
        {
            if (!resizeInternalBuffer(payloadLength))
            {
                resetAfterMessageDelivery();
                return SendResult.INVALID_MESSAGE;
            }
        }
        sessionStatistics.frameDecoded();
        return appendToInternalBuffer(payload, 0, payloadLength, session);
    }

    private void handleClose(final MutableDirectBuffer payload, final int payloadLength, final WebSocketSession session)
    {
        short closeReason = Constants.CLOSE_REASON_NORMAL;
        if (frameHeader.maskedPayloadLength > 1)
        {
            if (maskingKey != 0)
            {
                unmaskPayload(payload, 0, payload, 0, payloadLength);
            }
            closeReason = payload.getShort(0, NETWORK_BYTE_ORDER);
        }
        messageReceiver.onCloseMessage(closeReason, session);
    }

    private void handlePong(final MutableDirectBuffer payload, final int payloadLength, final WebSocketSession session)
    {
        if (session != null)
        {
            Logger.log(Category.HEARTBEAT, "Received pong on session %d, payload: %dB%n",
                session.id(), dstOffset);
        }
        if (maskingKey != 0)
        {
            unmaskPayload(payload, 0, payload, 0, payloadLength);
        }
        pingAgent.pongReceived(payload, 0, frameHeader.maskedPayloadLength);
    }

    private void handlePing(final MutableDirectBuffer payload, final int payloadLength, final WebSocketSession session)
    {
        if (session != null)
        {
            Logger.log(Category.HEARTBEAT, "Received ping on session %d, payload: %dB%n",
                session.id(), dstOffset);
        }
        if (payloadLength > PingAgent.MAX_PING_PAYLOAD_LENGTH)
        {
            sessionStatistics.invalidPingReceived();
        }
        else
        {
            if (maskingKey != 0)
            {
                unmaskPayload(payload, 0, payload, 0, payloadLength);
            }
            pingAgent.pingReceived(payload, 0, frameHeader.maskedPayloadLength);
        }
    }

    private int deliverDirect(
        final MutableDirectBuffer payload,
        final int payloadOffset,
        final int payloadLength,
        final WebSocketSession session)
    {
        if (maskingKey != 0)
        {
            unmaskPayload(payload, payloadOffset, payload, payloadOffset, payloadLength);
        }
        final int deliveryResult = publishToApplication(payload, payloadOffset, payloadLength, session);
        if (SendResult.OK == deliveryResult)
        {
            sessionStatistics.messageReceived();
            final int bytesConsumed = frameLength;
            resetAfterMessageDelivery();
            return bytesConsumed;
        }
        else if (SendResult.BACK_PRESSURE == deliveryResult)
        {
            dstBuffer.putBytes(dstOffset, payload, payloadOffset, payloadLength);
            dstOffset += payloadLength;
            hasQueuedMessage = true;
            return frameLength;
        }
        else
        {
            return deliveryResult;
        }
    }

    private int appendToInternalBuffer(
        final DirectBuffer payload,
        final int payloadOffset,
        final int payloadLength,
        final WebSocketSession session)
    {
        if (maskingKey == 0)
        {
            dstBuffer.putBytes(dstOffset, payload, payloadOffset, payloadLength);
            dstOffset += payloadLength;
        }
        else
        {
            unmaskPayload(payload, payloadOffset, dstBuffer, dstOffset, payloadLength);
            dstOffset += payloadLength;
        }

        if (frameHeader.isFin)
        {
            return deliverBufferedMessage(session);
        }
        return frameLength;
    }

    private int deliverBufferedMessage(final WebSocketSession session)
    {
        // cache value before reset();
        int bytesConsumed = frameLength;
        final int deliveryResult = publishToApplication(dstBuffer, 0, dstOffset, session);

        if (SendResult.OK == deliveryResult)
        {
            sessionStatistics.messageReceived();
            resetAfterMessageDelivery();
        }
        else if (SendResult.BACK_PRESSURE == deliveryResult)
        {
            hasQueuedMessage = true;
        }
        else if (SendResult.NOT_CONNECTED == deliveryResult)
        {
            bytesConsumed = 0;
        }
        else
        {
            throw new IllegalStateException("Invalid response code: " + deliveryResult);
        }

        return bytesConsumed;
    }

    private int publishToApplication(
        final DirectBuffer msg,
        final int offset,
        final int length,
        final WebSocketSession session)
    {
        final ContentType contentType = ContentType.forOpCode(initialFrameOpCode);

        final int deliveryResult = messageReceiver.onMessage(session, contentType, msg, offset, length);
        if (SendResult.OK == deliveryResult)
        {
            Logger.log(Category.DECODE, "Delivered %d to session %d%n", length, sessionId);
        }
        else if (SendResult.BACK_PRESSURE == deliveryResult)
        {
            sessionContainerStatistics.receiveBackPressure();
        }

        return deliveryResult;
    }

    private boolean resizeInternalBuffer(final int payloadLength)
    {
        final boolean bufferCapacityAvailable;
        final ByteBuffer oldBuffer = dstBuffer.byteBuffer();
        final int newSize = BitUtil.findNextPositivePowerOfTwo(dstOffset + payloadLength);
        if (newSize <= internalBufferMaxCapacity)
        {
            final ByteBuffer newBuffer = bufferPool.acquire(newSize);
            dstBuffer.wrap(newBuffer, 0, newSize);
            dstBuffer.putBytes(0, oldBuffer, dstOffset);
            bufferPool.release(oldBuffer);
            bufferCapacityAvailable = true;
        }
        else
        {
            bufferCapacityAvailable = false;
        }
        return bufferCapacityAvailable;
    }

    private boolean internalBufferIncreaseRequired(final int payloadLength)
    {
        return dstBuffer.capacity() < dstOffset + payloadLength;
    }

    private int assignPayloadLength()
    {
        payloadLength = frameHeader.maskedPayloadLength;
        if (frameHeader.frameSize == FrameSize.MEDIUM)
        {
            payloadLength = (int)Integer.toUnsignedLong(srcBuffer.getShort(2, NETWORK_BYTE_ORDER));
        }
        else if (frameHeader.frameSize == FrameSize.LARGE)
        {
            final long largePayloadLength = srcBuffer.getLong(2, NETWORK_BYTE_ORDER);
            if (largePayloadLength > MAX_PAYLOAD_SIZE)
            {
                return SendResult.INVALID_MESSAGE;
            }
            payloadLength = (int)largePayloadLength;
        }
        frameLength = payloadLength + frameHeader.payloadOffset;
        return payloadLength;
    }

    private void assignMaskingKey()
    {
        if (frameHeader.isMasked)
        {
            final int maskingKeyOffset = frameHeader.maskingKeyOffset;
            maskingKey = Integer.toUnsignedLong(srcBuffer.getInt(maskingKeyOffset));
            if (maskingKey != 0)
            {
                maskingKey = maskingKey << 32 | maskingKey;
                Logger.log(Category.DECODE, "Mask key: %d%n", maskingKey);
                maskingKeyBytes[0] = srcBuffer.getByte(maskingKeyOffset);
                maskingKeyBytes[1] = srcBuffer.getByte(maskingKeyOffset + 1);
                maskingKeyBytes[2] = srcBuffer.getByte(maskingKeyOffset + 2);
                maskingKeyBytes[3] = srcBuffer.getByte(maskingKeyOffset + 3);
            }
        }
        else
        {
            maskingKey = 0;
        }
    }

    private boolean isSingleCompleteMessage()
    {
        return frameHeader.isFin && dstOffset == 0;
    }

    private void unmaskPayload(
        final DirectBuffer srcBuffer,
        final int srcOffset,
        final MutableDirectBuffer dstBuffer,
        final int dstOffset,
        final int payloadLength)
    {
        int relativeSrcOffset = srcOffset;
        int relativeDstOffset = dstOffset;
        for (int i = 0; i < payloadLength >> CHUNK_SHIFT; i++)
        {
            dstBuffer.putLong(relativeDstOffset, srcBuffer.getLong(relativeSrcOffset) ^ maskingKey);
            relativeDstOffset += MASK_COPY_CHUNK_SIZE_BYTES;
            relativeSrcOffset += MASK_COPY_CHUNK_SIZE_BYTES;
        }

        final int bytesRemaining = payloadLength & CHUNK_MASK;
        if (bytesRemaining != 0)
        {
            unmaskTrailingBytes(srcBuffer, relativeSrcOffset, dstBuffer, relativeDstOffset, bytesRemaining);
        }
    }

    private void unmaskTrailingBytes(
        final DirectBuffer srcBuffer,
        final int srcOffset,
        final MutableDirectBuffer dstBuffer,
        final int dstOffset,
        final int length)
    {
        int relativeOffset = dstOffset;
        for (int i = 0; i < length; i++)
        {
            final byte maskedByte = srcBuffer.getByte(srcOffset + i);
            final byte unmaskedByte = (byte)(maskedByte ^ maskingKeyBytes[(maskingKeyIndex & 3)]);
            dstBuffer.putByte(relativeOffset++, unmaskedByte);
            maskingKeyIndex++;
        }
    }

    private void resetAfterMessageDelivery()
    {
        dstOffset = 0;
        maskingKey = 0;
        payloadLength = -1;
        frameLength = 0;
        maskingKeyIndex = 0;
//        frameHeader.reset();
        hasQueuedMessage = false;
        initialFrameOpCode = NO_OP_CODE;
    }

    boolean hasQueuedMessage()
    {
        return hasQueuedMessage;
    }

    void reset()
    {
        resetAfterMessageDelivery();
        bufferPool.release(dstBuffer.byteBuffer());
    }

    void init(final SessionStatistics sessionStatistics, final long sessionId)
    {
        this.sessionStatistics = sessionStatistics;
        this.sessionId = sessionId;
        dstBuffer.wrap(bufferPool.acquire(sessionConfig.sessionDecodeBufferSize()));
    }

    private static boolean headerDataAvailable(final ByteBuffer payload, final byte payloadLengthIndicator)
    {
        return payload.remaining() >= EncodingUtil.requestHeaderLengthByIndicator(payloadLengthIndicator);
    }
}
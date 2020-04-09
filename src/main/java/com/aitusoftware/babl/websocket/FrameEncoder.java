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

import static com.aitusoftware.babl.io.BufferUtil.increaseCapacity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import com.aitusoftware.babl.log.Category;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.monitoring.SessionContainerStatistics;
import com.aitusoftware.babl.monitoring.SessionStatistics;
import com.aitusoftware.babl.pool.BufferPool;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.config.SessionConfig;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

final class FrameEncoder
{
    private final int frameHeaderLength;
    private final int maxFramePayloadSize;
    private final int sendBufferMaxSize;
    private final BufferPool bufferPool;
    private final SessionDataListener sessionDataListener;
    private final boolean shouldMask;
    private final int headerPadding;
    private final byte maskBitmask;
    private final MutableDirectBuffer closeReasonBuffer = new UnsafeBuffer(new byte[BitUtil.SIZE_OF_SHORT]);
    private final SessionContainerStatistics sessionContainerStatistics;
    private final SessionConfig sessionConfig;
    private SessionStatistics sessionStatistics;
    private long sessionId;
    private ByteBuffer sendBuffer;

    FrameEncoder(
        final BufferPool bufferPool,
        final SessionDataListener sessionDataListener,
        final SessionConfig sessionConfig,
        final boolean shouldMask,
        final SessionContainerStatistics sessionContainerStatistics)
    {
        this.sessionConfig = sessionConfig;
        final int maxFrameSize = sessionConfig.maxWebSocketFrameLength();
        this.sendBufferMaxSize = sessionConfig.maxBufferSize();
        this.bufferPool = bufferPool;
        this.sendBuffer = bufferPool.acquire(sessionConfig.sendBufferSize());
        this.sessionDataListener = sessionDataListener;
        if (maxFrameSize <= 125)
        {
            frameHeaderLength = 2;
        }
        else if (maxFrameSize <= 65536)
        {
            frameHeaderLength = 4;
        }
        else
        {
            frameHeaderLength = 10;
        }
        maxFramePayloadSize = maxFrameSize - frameHeaderLength;
        this.shouldMask = shouldMask;
        this.headerPadding = shouldMask ? BitUtil.SIZE_OF_INT : 0;
        this.sessionContainerStatistics = sessionContainerStatistics;
        this.maskBitmask = (byte)(shouldMask ? 0b1000_0000 : 0b0000_0000);
    }

    FrameEncoder(
        final BufferPool bufferPool,
        final SessionDataListener sessionDataListener,
        final SessionConfig sessionConfig,
        final SessionContainerStatistics sessionContainerStatistics)
    {
        this(bufferPool, sessionDataListener, sessionConfig, false, sessionContainerStatistics);
    }

    void reset()
    {
        bufferPool.release(sendBuffer);
    }

    void init(final SessionStatistics sessionStatistics, final long sessionId)
    {
        this.sessionStatistics = sessionStatistics;
        this.sessionId = sessionId;
        sendBuffer = bufferPool.acquire(sessionConfig.sendBufferSize());
    }

    int encodePong(final DirectBuffer pingData, final int offset, final int length)
    {
        return encodeData(pingData, offset, length, Constants.OPCODE_PONG);
    }

    int encodePing(final DirectBuffer payload, final int offset, final int length)
    {
        return encodeData(payload, offset, length, Constants.OPCODE_PING);
    }

    int encodeClose(final short closeReason)
    {
        closeReasonBuffer.putShort(0, closeReason, Constants.NETWORK_BYTE_ORDER);
        return encodeData(closeReasonBuffer, 0, BitUtil.SIZE_OF_SHORT, Constants.OPCODE_CLOSE);
    }

    int encodeMessage(final ContentType contentType, final DirectBuffer src, final int offset, final int length)
    {
        final int initialFrameOpcode = contentType.opCode();
        return encodeData(src, offset, length, initialFrameOpcode);
    }

    private int encodeData(
        final DirectBuffer src,
        final int offset,
        final int length,
        final int initialFrameOpcode)
    {
        int frameCount = length / maxFramePayloadSize;
        if (frameCount * maxFramePayloadSize < length)
        {
            frameCount++;
        }

        final int bufferRequired = (frameCount * frameHeaderLength) + length + headerPadding;
        if (bufferRequired > sendBufferMaxSize)
        {
            return SendResult.INVALID_MESSAGE;
        }
        int relativeOffset = sendBuffer.position();
        if (bufferRequired > sendBuffer.remaining())
        {
            final int requiredBufferCapacity = BitUtil.findNextPositivePowerOfTwo(relativeOffset + bufferRequired);
            if (requiredBufferCapacity <= sendBufferMaxSize)
            {
                sendBuffer = increaseCapacity(sendBuffer, requiredBufferCapacity, bufferPool);
            }
            else
            {
                sessionStatistics.sendBackPressure();
                return SendResult.BACK_PRESSURE;
            }
        }
        Logger.log(Category.ENCODE, "Encoding %d bytes on session %d%n", length, sessionId);
        int srcOffset = offset;
        int remainingLength = length;
        int opCode = initialFrameOpcode;
        for (int i = 0; i < frameCount; i++)
        {
            final int framePayloadLength = Math.min(maxFramePayloadSize, remainingLength);
            final byte flags = headerFlags(opCode, i == frameCount - 1);
            sendBuffer.put(relativeOffset, flags);
            relativeOffset++;
            relativeOffset += addLengthHeader(relativeOffset, framePayloadLength);
            if (shouldMask)
            {
                sendBuffer.putInt(relativeOffset, 0);
                relativeOffset += 4;
            }
            src.getBytes(srcOffset, sendBuffer, relativeOffset, framePayloadLength);
            relativeOffset += framePayloadLength;
            srcOffset += framePayloadLength;
            remainingLength -= framePayloadLength;
            opCode = Constants.OPCODE_CONTINUATION;
            sessionStatistics.frameEncoded();
        }
        sendBuffer.position(relativeOffset);
        sessionStatistics.sendBufferedBytes(relativeOffset);
        sessionStatistics.messageSent();
        sessionDataListener.sendDataAvailable();

        return SendResult.OK;
    }

    int doSendWork(final SocketChannel channel) throws IOException
    {
        if (sendBuffer.position() != 0)
        {
            sendBuffer.flip();
            int written;
            try
            {
                written = channel.write(sendBuffer);
                sessionStatistics.bytesWritten(written);
                sessionContainerStatistics.bytesWritten(written);
            }
            catch (final IOException e)
            {
                written = -1;
            }

            if (-1 == written)
            {
                return SendResult.NOT_CONNECTED;
            }
            if (sendBuffer.hasRemaining())
            {
                sendBuffer.compact();
            }
            else
            {
                sendBuffer.clear();
                sessionDataListener.sendDataProcessed();
            }
            sessionStatistics.sendBufferedBytes(sendBuffer.position());
            return 1;
        }
        return 0;
    }

    ByteBuffer sendBuffer()
    {
        return sendBuffer;
    }

    private int addLengthHeader(final int relativeOffset, final int framePayloadLength)
    {
        final int lengthOffset;
        if (EncodingUtil.isSmallResponseMessage(
            EncodingUtil.responseHeaderLengthByPayloadLength(framePayloadLength)))
        {
            sendBuffer.put(relativeOffset, (byte)(0xFF & (maskBitmask | framePayloadLength)));
            lengthOffset = 1;
        }
        else if (EncodingUtil.isMediumResponseMessage(
            EncodingUtil.responseHeaderLengthByPayloadLength(framePayloadLength)))
        {
            sendBuffer.put(relativeOffset, (byte)(0xFF & (maskBitmask | 126)));
            sendBuffer.order(Constants.NETWORK_BYTE_ORDER).putShort(relativeOffset + 1, (short)framePayloadLength);
            lengthOffset = 3;
        }
        else
        {
            sendBuffer.put(relativeOffset, (byte)(0xFF & (maskBitmask | 127)));
            sendBuffer.order(Constants.NETWORK_BYTE_ORDER).putLong(relativeOffset + 1, framePayloadLength);
            lengthOffset = 9;
        }
        return lengthOffset;
    }

    private static byte headerFlags(final int opCode, final boolean isFin)
    {
        return (byte)(isFin ? 0b1000_0000 | opCode & 0xFF : opCode & 0xFF);
    }
}
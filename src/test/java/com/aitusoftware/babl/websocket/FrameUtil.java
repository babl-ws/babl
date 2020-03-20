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

import static com.aitusoftware.babl.websocket.EncodingUtil.requestHeaderLengthByIndicator;
import static com.aitusoftware.babl.websocket.EncodingUtil.requestHeaderLengthByPayloadLength;
import static com.aitusoftware.babl.websocket.EncodingUtil.responseHeaderLengthByPayloadLength;
import static com.google.common.truth.Truth.assertThat;

import java.nio.ByteBuffer;

final class FrameUtil
{
    static void writeWebSocketFrame(final byte[] payload, final ByteBuffer dst)
    {
        writeWebSocketFrame(payload, payload.length, dst);
    }

    static void writeWebSocketFrame(final byte[] payload, final int lengthToWrite, final ByteBuffer dst)
    {
        writeWebSocketFrame(payload, lengthToWrite, dst, 0, 0);
    }

    static void writeContinuationWebSocketFrame(
        final byte[] payload, final int lengthToWrite,
        final ByteBuffer dst, final int maskingKey,
        final int maskingKeyOffset)
    {
        writeWebSocketFrame(payload, lengthToWrite, dst, maskingKey, maskingKeyOffset, false, Constants.OPCODE_BINARY);
    }

    static void writeWebSocketFrame(
        final byte[] payload, final int lengthToWrite,
        final ByteBuffer dst, final int maskingKey,
        final int maskingKeyOffset)
    {
        writeWebSocketFrame(payload, lengthToWrite, dst, maskingKey, maskingKeyOffset, true, Constants.OPCODE_BINARY);
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
        final int headerLength = requestHeaderLengthByPayloadLength(payload.length);

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

    static int frameLength(final int messageLength)
    {
        return messageLength + requestHeaderLengthByIndicator(messageLength);
    }

    static void assertWebSocketFrame(final int position, final int payloadLength, final ByteBuffer src)
    {
        assertThat(src.position()).isEqualTo(position);
        assertThat(src.get(1) & 0b0111_1111).isEqualTo(payloadLength);
        assertThat(src.limit()).isEqualTo(
            payloadLength + responseHeaderLengthByPayloadLength(payloadLength) + position);
    }

    static void assertWebSocketFrame(
        final ByteBuffer src, final int offset, final int payloadLength,
        final int expectedOpcode, final boolean isFin)
    {
        final int payloadLengthIndicator = src.get(offset + 1) & 0b0111_1111;
        final int headerLength = EncodingUtil.responseHeaderLengthByIndicator(payloadLengthIndicator);
        final int encodedPayloadLength;
        if (EncodingUtil.isSmallResponseMessage(headerLength))
        {
            encodedPayloadLength = payloadLengthIndicator;
        }
        else if (EncodingUtil.isMediumResponseMessage(headerLength))
        {
            encodedPayloadLength = src.getShort(offset + 2);
        }
        else if (EncodingUtil.isLargeResponseMessage(headerLength))
        {
            encodedPayloadLength = (int)src.getLong(offset + 2);
        }
        else
        {
            throw new IllegalArgumentException();
        }
        assertThat(encodedPayloadLength).isEqualTo(payloadLength);
        final byte flags = src.get(offset);
        assertThat(DecodingUtil.isFin(flags)).isEqualTo(isFin);
        assertThat(DecodingUtil.opCode(flags)).isEqualTo(expectedOpcode);
    }

    static void writePingFrame(
        final byte[] payload,
        final int length,
        final ByteBuffer dst,
        final int maskingKey,
        final int maskingKeyOffset)
    {
        writeWebSocketFrame(payload, length, dst, maskingKey, maskingKeyOffset, true, Constants.OPCODE_PING);
    }
    /*
Frame format:
​​
      0                   1                   2                   3
      0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     +-+-+-+-+-------+-+-------------+-------------------------------+
     |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
     |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
     |N|V|V|V|       |S|             |   (if payload len==126/127)   |
     | |1|2|3|       |K|             |                               |
     +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
     |     Extended payload length continued, if payload len == 127  |
     + - - - - - - - - - - - - - - - +-------------------------------+
     |                               |Masking-key, if MASK set to 1  |
     +-------------------------------+-------------------------------+
     | Masking-key (continued)       |          Payload Data         |
     +-------------------------------- - - - - - - - - - - - - - - - +
     :                     Payload Data continued ...                :
     + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
     |                     Payload Data continued ...                |
     +---------------------------------------------------------------+
     */

}
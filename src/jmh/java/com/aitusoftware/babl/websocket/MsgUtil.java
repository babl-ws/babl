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

final class MsgUtil
{
    static void writeWebSocketFrame(
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

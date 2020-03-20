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

import org.agrona.BitUtil;

final class FrameHeader
{
    private static final int MASKED_MASK = 0b1000_0000;
    private static final int LENGTH_MASK = ~MASKED_MASK & 0xFF;

    int maskingKeyOffset;
    int payloadOffset;
    int maskedPayloadLength;
    boolean isMasked;
    boolean isFin;
    int opCode = -1;
    FrameSize frameSize;

    void reset()
    {
        maskingKeyOffset = 0;
        payloadOffset = 0;
        maskedPayloadLength = 0;
        isMasked = false;
        isFin = false;
        opCode = -1;
        frameSize = null;
    }

    void set(final byte flags, final byte payloadLengthIndicator)
    {
        isFin = DecodingUtil.isFin(flags);
        isMasked = (payloadLengthIndicator & MASKED_MASK) != 0;
        opCode = DecodingUtil.opCode(flags);
        maskedPayloadLength = payloadLengthIndicator & LENGTH_MASK;
        payloadOffset = EncodingUtil.requestHeaderLengthByIndicator(maskedPayloadLength);
        if (!isMasked)
        {
            payloadOffset -= BitUtil.SIZE_OF_INT;
        }
        maskingKeyOffset = payloadOffset - BitUtil.SIZE_OF_INT;
        frameSize = FrameSize.MEDIUM;
        if (maskedPayloadLength <= Constants.SMALL_MESSAGE_SIZE_INDICATOR)
        {
            frameSize = FrameSize.SMALL;
        }
        else if (maskedPayloadLength == Constants.LARGE_MESSAGE_SIZE_INDICATOR)
        {
            frameSize = FrameSize.LARGE;
        }
    }

    @Override
    public String toString()
    {
        return "RequestHeader{" +
            "isMasked=" + isMasked +
            ", isFin=" + isFin +
            ", opCode=" + opCode +
            ", frameSize=" + frameSize +
            '}';
    }
}
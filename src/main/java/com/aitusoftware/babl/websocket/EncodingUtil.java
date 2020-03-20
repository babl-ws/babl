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

final class EncodingUtil
{
    static final int SMALL_MESSAGE_REQUEST_HEADER_LENGTH = 6;
    static final int MEDIUM_MESSAGE_REQUEST_HEADER_LENGTH = 8;
    static final int LARGE_MESSAGE_REQUEST_HEADER_LENGTH = 14;

    static final int SMALL_MESSAGE_RESPONSE_HEADER_LENGTH = 2;
    static final int MEDIUM_MESSAGE_RESPONSE_HEADER_LENGTH = 4;
    static final int LARGE_MESSAGE_RESPONSE_HEADER_LENGTH = 10;

    static final int SMALL_MESSAGE_LENGTH_INDICATOR = 125;
    static final int MEDIUM_MESSAGE_LENGTH_INDICATOR = 126;
    static final int LARGE_MESSAGE_LENGTH_INDICATOR = 127;

    static final int SMALL_MESSAGE_PAYLOAD_LIMIT = 125;
    static final int MEDIUM_MESSAGE_PAYLOAD_LIMIT = 65536;

    static boolean isSmallRequestMessage(final int headerLength)
    {
        return headerLength == SMALL_MESSAGE_REQUEST_HEADER_LENGTH;
    }

    static boolean isMediumRequestMessage(final int headerLength)
    {
        return headerLength == MEDIUM_MESSAGE_REQUEST_HEADER_LENGTH;
    }

    static boolean isLargeRequestMessage(final int headerLength)
    {
        return headerLength == LARGE_MESSAGE_REQUEST_HEADER_LENGTH;
    }

    static boolean isSmallResponseMessage(final int headerLength)
    {
        return headerLength == SMALL_MESSAGE_RESPONSE_HEADER_LENGTH;
    }

    static boolean isMediumResponseMessage(final int headerLength)
    {
        return headerLength == MEDIUM_MESSAGE_RESPONSE_HEADER_LENGTH;
    }

    static boolean isLargeResponseMessage(final int headerLength)
    {
        return headerLength == LARGE_MESSAGE_RESPONSE_HEADER_LENGTH;
    }

    static int responseHeaderLengthByIndicator(final int payloadLengthIndicator)
    {
        if (payloadLengthIndicator <= SMALL_MESSAGE_LENGTH_INDICATOR)
        {
            return SMALL_MESSAGE_RESPONSE_HEADER_LENGTH;
        }
        if (payloadLengthIndicator == MEDIUM_MESSAGE_LENGTH_INDICATOR)
        {
            return MEDIUM_MESSAGE_RESPONSE_HEADER_LENGTH;
        }
        if (payloadLengthIndicator == LARGE_MESSAGE_LENGTH_INDICATOR)
        {
            return LARGE_MESSAGE_RESPONSE_HEADER_LENGTH;
        }
        throw new IllegalArgumentException("Not a payload length indicator: " + payloadLengthIndicator);
    }

    static int requestHeaderLengthByIndicator(final int payloadLengthIndicator)
    {
        if (payloadLengthIndicator <= SMALL_MESSAGE_LENGTH_INDICATOR)
        {
            return SMALL_MESSAGE_REQUEST_HEADER_LENGTH;
        }
        if (payloadLengthIndicator == MEDIUM_MESSAGE_LENGTH_INDICATOR)
        {
            return MEDIUM_MESSAGE_REQUEST_HEADER_LENGTH;
        }
        if (payloadLengthIndicator == LARGE_MESSAGE_LENGTH_INDICATOR)
        {
            return LARGE_MESSAGE_REQUEST_HEADER_LENGTH;
        }
        throw new IllegalArgumentException("Not a payload length indicator: " + payloadLengthIndicator);
    }

    static int responseHeaderLengthByPayloadLength(final int payloadLength)
    {
        if (payloadLength <= SMALL_MESSAGE_PAYLOAD_LIMIT)
        {
            return SMALL_MESSAGE_RESPONSE_HEADER_LENGTH;
        }
        if (payloadLength <= MEDIUM_MESSAGE_PAYLOAD_LIMIT)
        {
            return MEDIUM_MESSAGE_RESPONSE_HEADER_LENGTH;
        }
        return LARGE_MESSAGE_RESPONSE_HEADER_LENGTH;
    }

    static int requestHeaderLengthByPayloadLength(final int payloadLength)
    {
        if (payloadLength <= SMALL_MESSAGE_PAYLOAD_LIMIT)
        {
            return SMALL_MESSAGE_REQUEST_HEADER_LENGTH;
        }
        if (payloadLength <= MEDIUM_MESSAGE_PAYLOAD_LIMIT)
        {
            return MEDIUM_MESSAGE_REQUEST_HEADER_LENGTH;
        }
        return LARGE_MESSAGE_REQUEST_HEADER_LENGTH;
    }
}

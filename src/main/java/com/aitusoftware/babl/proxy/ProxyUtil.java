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
package com.aitusoftware.babl.proxy;

import com.aitusoftware.babl.websocket.SendResult;

import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.ControlledFragmentHandler;

final class ProxyUtil
{
    ProxyUtil()
    {
    }

    static ControlledFragmentHandler.Action sendResultToAction(final int sendResult)
    {
        if (sendResult == SendResult.BACK_PRESSURE)
        {
            return ControlledFragmentHandler.Action.ABORT;
        }
        return ControlledFragmentHandler.Action.CONTINUE;
    }

    static long acquireBuffer(final int length, final Publication publication, final BufferClaim bufferClaim)
    {
        long position;
        position = publication.tryClaim(length, bufferClaim);
        if (position == Publication.ADMIN_ACTION)
        {
            for (int i = 0; i < 3 && position == Publication.ADMIN_ACTION; i++)
            {
                position = publication.tryClaim(length, bufferClaim);
            }
        }
        return position;
    }

    static int offerResultToSendResult(final long result)
    {
        if (result == Publication.BACK_PRESSURED)
        {
            return SendResult.BACK_PRESSURE;
        }
        else if (result == Publication.CLOSED || result == Publication.NOT_CONNECTED)
        {
            return SendResult.NOT_CONNECTED;
        }
        else if (result == Publication.MAX_POSITION_EXCEEDED)
        {
            return SendResult.INVALID_MESSAGE;
        }
        return SendResult.BACK_PRESSURE;
    }
}

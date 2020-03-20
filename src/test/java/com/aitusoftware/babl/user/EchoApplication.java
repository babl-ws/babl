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
package com.aitusoftware.babl.user;

import com.aitusoftware.babl.websocket.DisconnectReason;
import com.aitusoftware.babl.websocket.SendResult;
import com.aitusoftware.babl.websocket.Session;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;

@SuppressWarnings("NonAtomicOperationOnVolatileField")
public final class EchoApplication implements Application
{
    private final MutableDirectBuffer buffer = new ExpandableDirectByteBuffer(512);
    private final boolean shouldProvideBackPressure;
    private long messageCount = 0L;
    private volatile int sessionOpenedCount;
    private volatile int sessionClosedCount;

    public EchoApplication(final boolean shouldProvideBackPressure)
    {
        this.shouldProvideBackPressure = shouldProvideBackPressure;
    }

    @Override
    public int onSessionConnected(final Session session)
    {
        sessionOpenedCount++;
        return SendResult.OK;
    }

    @Override
    public int onSessionDisconnected(final Session session, final DisconnectReason reason)
    {
        sessionClosedCount++;
        return SendResult.OK;
    }

    @Override
    public int onSessionMessage(
        final Session session,
        final ContentType contentType,
        final DirectBuffer msg,
        final int offset,
        final int length)
    {
        if (shouldProvideBackPressure && (messageCount++ & 3) == 0)
        {
            System.out.printf("Back-pressuring message %s%n", msg.getStringWithoutLengthAscii(0, length));
            return SendResult.BACK_PRESSURE;
        }
        buffer.putBytes(0, msg, offset, length);
        int sendResult;
        do
        {
            sendResult = session.send(contentType, buffer, 0, length);
        }
        while (sendResult != SendResult.OK);

        return sendResult;
    }

    public int getSessionOpenedCount()
    {
        return sessionOpenedCount;
    }

    public int getSessionClosedCount()
    {
        return sessionClosedCount;
    }
}

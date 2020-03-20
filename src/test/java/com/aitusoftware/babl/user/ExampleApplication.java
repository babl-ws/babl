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

import java.nio.charset.StandardCharsets;

import com.aitusoftware.babl.websocket.DisconnectReason;
import com.aitusoftware.babl.websocket.SendResult;
import com.aitusoftware.babl.websocket.Session;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;

public final class ExampleApplication implements Application
{
    private final MutableDirectBuffer buffer = new ExpandableDirectByteBuffer(512);

    @Override
    public int onSessionConnected(final Session session)
    {
        System.out.printf("Session %d connected%n", session.id());
        return SendResult.OK;
    }

    @Override
    public int onSessionDisconnected(final Session session, final DisconnectReason reason)
    {
        System.out.printf("Session %d disconnected due to %s%n", session.id(), reason.name());
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
        final byte[] content = new byte[length];
        msg.getBytes(offset, content);
        System.out.printf("Received msg %s from session %d%n",
            new String(content, StandardCharsets.UTF_8), session.id());
        buffer.putBytes(0, msg, offset, length);
        int sendResult;
        do
        {
            sendResult = session.send(contentType, buffer, 0, length);
        }
        while (sendResult != SendResult.OK);

        return sendResult;
    }
}
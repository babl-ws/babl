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

import com.aitusoftware.babl.user.Application;
import com.aitusoftware.babl.user.ContentType;

import org.agrona.DirectBuffer;

final class MessageDispatcher implements MessageReceiver
{
    private final Application application;

    MessageDispatcher(final Application application)
    {
        this.application = application;
    }

    @Override
    public int onMessage(
        final WebSocketSession session,
        final ContentType contentType,
        final DirectBuffer buffer,
        final int offset,
        final int length)
    {
        return application.onSessionMessage(session, contentType, buffer, offset, length);
    }

    @Override
    public void onCloseMessage(final short closeReason, final WebSocketSession session)
    {
        session.onCloseMessage(closeReason);
    }
}

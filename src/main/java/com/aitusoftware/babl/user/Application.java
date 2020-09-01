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
import com.aitusoftware.babl.websocket.Session;

import org.agrona.DirectBuffer;

/**
 * User code should implement this interface to handle
 * web-socket messages.
 *
 * Implementations MAY cache the <code>Session</code> objects when opened, and until closed.
 * Implementations MUST NOT use a <code>Session</code> object after <code>onSessionDisconnected</code>
 * has been called.
 * Implementations MUST NOT use a <code>Session</code> object from another thread.
 */
public interface Application
{
    /**
     * Notification that a session has been created in the server.
     * Implementations of this method should always return
     * {@code SendResult.OK}.
     *
     * @param session the newly connected session
     * @return an indicator of success
     */
    int onSessionConnected(
        Session session);

    /**
     * Notification that a session has been disconnected in the server.
     * Implementations of this method should always return
     * {@code SendResult.OK}.
     *
     * @param session the newly disconnected session
     * @param reason the reason for closing
     * @return an indicator of success
     */
    int onSessionDisconnected(
        Session session,
        DisconnectReason reason);

    /**
     * Called when a complete message is received from a web socket peer.
     * Implementations of this method can use the return code to
     * indicate if there is back-pressure experienced
     * when attempting to send outbound messages back to the session.
     *
     * @param session     the session that received the message
     * @param contentType the content-type of the message
     * @param msg         the content of the message
     * @param offset      the message offset within the buffer
     * @param length      the length of the message
     * @return an indicator of success
     */
    int onSessionMessage(
        Session session,
        ContentType contentType,
        DirectBuffer msg,
        int offset,
        int length);
}
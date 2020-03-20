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

import org.agrona.collections.LongHashSet;

final class TrackingSessionDataListener
{
    final LongHashSet receiveWorkAvailable = new LongHashSet();
    final LongHashSet sendWorkAvailable = new LongHashSet();
    final LongHashSet toRemove = new LongHashSet();

    void sendDataAvailable(final long sessionId)
    {
        sendWorkAvailable.add(sessionId);
    }

    void sendDataProcessed(final long sessionId)
    {
        sendWorkAvailable.remove(sessionId);
    }

    void receiveDataAvailable(final long sessionId)
    {
        receiveWorkAvailable.add(sessionId);
    }

    void receiveDataProcessed(final long sessionId)
    {
        receiveWorkAvailable.remove(sessionId);
    }

    void sessionClosed(final long sessionId)
    {
        remove(sessionId);
        toRemove.add(sessionId);
    }

    private void remove(final long sessionId)
    {
        receiveWorkAvailable.remove(sessionId);
        sendWorkAvailable.remove(sessionId);
    }
}
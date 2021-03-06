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
package com.aitusoftware.babl.monitoring;

import com.aitusoftware.babl.websocket.WebSocketSession;

public abstract class SessionStatistics
{
    public abstract void bytesRead(int bytesRead);

    public abstract void bytesWritten(int bytesWritten);

    public abstract void receiveBufferedBytes(int receiveBufferedBytes);

    public abstract void sendBufferedBytes(int sendBufferedBytes);

    public abstract void frameDecoded();

    public abstract void messageReceived();

    public abstract void frameEncoded();

    public abstract void messageSent();

    public abstract void invalidMessageReceived();

    public abstract void invalidPingReceived();

    public abstract void sendBackPressure();

    public abstract void reset(long sessionId, WebSocketSession.SessionState sessionState);
}
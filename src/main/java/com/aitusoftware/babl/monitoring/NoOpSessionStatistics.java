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

public final class NoOpSessionStatistics extends SessionStatistics
{
    @Override
    public void bytesRead(final int bytesRead)
    {

    }

    @Override
    public void bytesWritten(final int bytesWritten)
    {

    }

    @Override
    public void receiveBufferedBytes(final int receiveBufferedBytes)
    {

    }

    @Override
    public void sendBufferedBytes(final int sendBufferedBytes)
    {

    }

    @Override
    public void frameDecoded()
    {

    }

    @Override
    public void messageReceived()
    {

    }

    @Override
    public void frameEncoded()
    {

    }

    @Override
    public void messageSent()
    {

    }

    @Override
    public void invalidMessageReceived()
    {

    }

    @Override
    public void invalidPingReceived()
    {

    }

    @Override
    public void sendBackPressure()
    {

    }

    @Override
    public void reset(final long sessionId, final WebSocketSession.SessionState sessionState)
    {

    }
}

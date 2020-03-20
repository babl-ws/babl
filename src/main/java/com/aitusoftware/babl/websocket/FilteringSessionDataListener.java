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

final class FilteringSessionDataListener extends SessionDataListener
{
    private final TrackingSessionDataListener delegate;
    private long sessionId;
    private boolean notifiedReceiveData;
    private boolean notifiedSendData;
    private boolean notifiedClosed;

    FilteringSessionDataListener(final TrackingSessionDataListener delegate)
    {
        this.delegate = delegate;
    }

    @Override
    void init(final long sessionId)
    {
        this.sessionId = sessionId;
        notifiedClosed = false;
        notifiedReceiveData = false;
        notifiedSendData = false;
    }

    @Override
    void sendDataAvailable()
    {
        if (!notifiedSendData)
        {
            delegate.sendDataAvailable(sessionId);
            notifiedSendData = true;
        }
    }

    @Override
    void sendDataProcessed()
    {
        if (notifiedSendData)
        {
            delegate.sendDataProcessed(sessionId);
            notifiedSendData = false;
        }
    }

    @Override
    void receiveDataAvailable()
    {
        if (!notifiedReceiveData)
        {
            delegate.receiveDataAvailable(sessionId);
            notifiedReceiveData = true;
        }
    }

    @Override
    void receiveDataProcessed()
    {
        if (notifiedReceiveData)
        {
            delegate.receiveDataProcessed(sessionId);
            notifiedReceiveData = false;
        }
    }

    @Override
    void sessionClosed()
    {
        if (!notifiedClosed)
        {
            delegate.sessionClosed(sessionId);
            notifiedClosed = true;
        }
    }
}
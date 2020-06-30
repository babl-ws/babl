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
package com.aitusoftware.babl.io;

import java.io.UncheckedIOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

import com.aitusoftware.babl.websocket.WebSocketSession;

import org.agrona.concurrent.Agent;
import org.agrona.nio.TransportPoller;

/**
 * Agent used to perform select operations on connected sockets.
 */
public final class WebSocketPoller extends TransportPoller implements Agent
{
    private final Consumer<WebSocketSession> readyToReadListener;
    private final int sessionPollLimit;

    /**
     * Constructor.
     *
     * @param readyToReadListener notifier for sessions that have receive data available
     * @param sessionPollLimit    maximum number of sessions to process per select call
     */
    public WebSocketPoller(
        final Consumer<WebSocketSession> readyToReadListener,
        final int sessionPollLimit)
    {
        this.readyToReadListener = readyToReadListener;
        this.sessionPollLimit = sessionPollLimit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int doWork() throws Exception
    {
        if (selector.selectNow() != 0)
        {
            final SelectionKey[] keys = selectedKeySet.keys();
            for (int i = 0; i < selectedKeySet.size(); i++)
            {
                final SelectionKey key = keys[i];
                final WebSocketSession session = (WebSocketSession)key.attachment();
                if (session.isClosed())
                {
                    key.cancel();
                }
                else
                {
                    readyToReadListener.accept(session);
                }
            }
            selectedKeySet.reset();
            return 1;
        }
        return 0;
    }

    /**
     * Register a {@code WebSocketSession} and its associated {@code SocketChannel}.
     *
     * @param session the web socket session
     * @param channel the socket channel
     */
    public void register(final WebSocketSession session, final SocketChannel channel)
    {
        try
        {
            channel.register(selector, SelectionKey.OP_READ, session);
        }
        catch (final ClosedChannelException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String roleName()
    {
        return "web-socket-poller";
    }
}
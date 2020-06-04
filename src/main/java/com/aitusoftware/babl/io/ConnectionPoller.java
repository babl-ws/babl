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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Queue;

import com.aitusoftware.babl.config.SocketConfig;
import com.aitusoftware.babl.websocket.routing.ConnectionRouter;

import org.agrona.CloseHelper;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.nio.TransportPoller;

/**
 * Agent used to perform select operations on a {@code ServerSocket}.
 */
public final class ConnectionPoller extends TransportPoller implements Agent
{
    private final ServerSocketChannel serverSocket;
    private final Queue<SocketChannel>[] toServerChannels;
    private final IdleStrategy idleStrategy;
    private final SocketConfig socketConfig;
    private final ConnectionRouter connectionRouter;
    private int serverIndex = 0;

    /**
     * Constructor.
     * @param serverSocket     the server socket to select on
     * @param toServerChannels thread-safe queues that will be polled by session container instances
     * @param idleStrategy     idle strategy to use when queue is full
     * @param socketConfig     configuration to apply to opened sockets
     * @param connectionRouter indicates what server instance the new connection should be dispatched to
     */
    public ConnectionPoller(
        final ServerSocketChannel serverSocket,
        final Queue<SocketChannel>[] toServerChannels,
        final IdleStrategy idleStrategy,
        final SocketConfig socketConfig,
        final ConnectionRouter connectionRouter)
    {
        this.serverSocket = serverSocket;
        this.toServerChannels = toServerChannels;
        this.idleStrategy = idleStrategy;
        this.socketConfig = socketConfig;
        this.connectionRouter = connectionRouter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStart()
    {
        try
        {
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);
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
    public int doWork() throws Exception
    {
        final int selected = selector.select(1000);

        if (selected != 0)
        {
            selectedKeySet.forEach(selectionKey ->
            {
                try
                {
                    final SocketChannel channel = ((ServerSocketChannel)selectionKey.channel()).accept();
                    channel.configureBlocking(false);
                    socketConfig.configureChannel(channel);
                    final Queue<SocketChannel> toServerChannel = toServerChannels[serverIndex];
                    serverIndex++;
                    if (serverIndex == toServerChannels.length)
                    {
                        serverIndex = 0;
                    }
                    while (!toServerChannel.offer(channel))
                    {
                        idleStrategy.idle();
                    }
                }
                catch (final IOException e)
                {
                    throw new UncheckedIOException(e);
                }
                return 1;
            });
            selectedKeySet.reset();
        }
        return selected;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClose()
    {
        CloseHelper.closeAll(selector, serverSocket);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String roleName()
    {
        return "babl-connection-poller";
    }
}
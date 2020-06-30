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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.aitusoftware.babl.config.SessionConfig;
import com.aitusoftware.babl.config.SocketConfig;
import com.aitusoftware.babl.monitoring.NoOpSessionContainerStatistics;
import com.aitusoftware.babl.monitoring.NoOpSessionStatistics;
import com.aitusoftware.babl.monitoring.SessionStatistics;
import com.aitusoftware.babl.pool.BufferPool;
import com.aitusoftware.babl.user.ContentType;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.SystemEpochClock;

/**
 * Allocation-free web-socket client used for testing purposes.
 */
public final class Client
{
    private final SocketConfig socketConfig = new SocketConfig();
    private final SessionConfig sessionConfig = new SessionConfig();
    private final ClientEventHandler clientEventHandler;
    private final FrameEncoder frameEncoder;
    private final FrameDecoder frameDecoder;
    private final ByteBuffer receiveBuffer;
    private final SessionStatistics sessionStatistics = new NoOpSessionStatistics();
    private final NoOpSessionContainerStatistics serverStatistics = new NoOpSessionContainerStatistics();
    private HttpRequestDetails httpRequestDetails;
    private SocketChannel channel;
    private boolean closed;

    public Client(final ClientEventHandler clientEventHandler)
    {
        this.clientEventHandler = clientEventHandler;
        final BufferPool bufferPool = new BufferPool();
        this.frameEncoder = new FrameEncoder(
            bufferPool, new NoOpSessionDataListener(), sessionConfig, true, serverStatistics);
        final PingAgent pingAgent = new PingAgent(
            frameEncoder, new SystemEpochClock(), TimeUnit.NANOSECONDS.toMillis(sessionConfig.pingIntervalNanos()),
            TimeUnit.NANOSECONDS.toMillis(sessionConfig.pongResponseTimeoutNanos()), new NoOpSessionDataListener());
        this.frameDecoder = new FrameDecoder(
            new ClientEventHandlerAdapter(clientEventHandler),
            sessionConfig, bufferPool, pingAgent, false,
            serverStatistics);
        receiveBuffer = ByteBuffer.allocateDirect(sessionConfig.receiveBufferSize());
        this.frameDecoder.init(sessionStatistics, 0L);
        this.frameEncoder.init(sessionStatistics, 0L);
    }

    public void connect(final URL url) throws IOException
    {
        channel = SocketChannel.open(new InetSocketAddress(url.getHost(), url.getPort()));
        channel.configureBlocking(false);
        socketConfig.configureChannel(channel);
        this.httpRequestDetails = new HttpRequestDetails(
            url.getHost() + ":" + url.getPort(), url.getPath());
    }

    public boolean upgradeConnection() throws IOException
    {
        final StringBuilder upgradeRequest = new StringBuilder("GET ")
            .append(httpRequestDetails.uri).append(" HTTP/1.1\r\n")
            .append("upgrade: websocket\r\n")
            .append("connection: upgrade\r\n")
            .append("sec-websocket-key: 01HBuwW196c3NpvZCS8ZAw==\r\n")
            .append("host: ").append(httpRequestDetails.host).append("\r\n")
            .append("sec-websocket-version: 13\r\n\r\n");
        receiveBuffer.put(upgradeRequest.toString().getBytes(StandardCharsets.UTF_8));
        receiveBuffer.flip();
        while (receiveBuffer.remaining() != 0)
        {
            channel.write(receiveBuffer);
        }
        receiveBuffer.clear();
        while (0 == channel.read(receiveBuffer))
        {
            LockSupport.parkNanos(100L);
        }
        receiveBuffer.flip();
        final byte[] tmp = new byte[receiveBuffer.remaining()];
        receiveBuffer.get(tmp);
        final String response = new String(tmp, StandardCharsets.UTF_8);
        if (response.toLowerCase().contains("101 switching protocols") && response.contains("\r\n\r\n"))
        {
            receiveBuffer.clear();
            return true;
        }
        return false;
    }

    public boolean offer(
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final ContentType contentType)
    {
        return frameEncoder.encodeMessage(contentType, buffer, offset, length) == SendResult.OK;
    }

    public int doWork() throws IOException
    {
        if (closed)
        {
            return 0;
        }
        final int sendWork = frameEncoder.doSendWork(channel);
        if (sendWork == SendResult.NOT_CONNECTED)
        {
            clientEventHandler.onConnectionClosed();
            closed = true;
            return 1;
        }
        return doReceiveWork() + sendWork;
    }

    public SessionConfig sessionConfig()
    {
        return sessionConfig;
    }

    public SocketConfig socketConfig()
    {
        return socketConfig;
    }

    private int doReceiveWork() throws IOException
    {
        int workDone = channel.read(receiveBuffer);
        if (workDone == -1)
        {
            clientEventHandler.onConnectionClosed();
            closed = true;
            workDone = 1;
        }

        if (receiveBuffer.position() == 0)
        {
            workDone = 0;
        }
        else
        {
            receiveBuffer.flip();
            for (int i = 0; i < 10; i++)
            {
                final int decode = frameDecoder.decode(receiveBuffer, null);
                if (receiveBuffer.remaining() == 0 || decode < 1)
                {
                    break;
                }
                workDone++;
                receiveBuffer.position(receiveBuffer.position() + decode);
            }
            if (receiveBuffer.remaining() != 0)
            {
                receiveBuffer.compact();
            }
            else
            {
                receiveBuffer.clear();
            }
        }
        return workDone;
    }

    private static class ClientEventHandlerAdapter implements MessageReceiver
    {
        private final ClientEventHandler clientEventHandler;

        ClientEventHandlerAdapter(final ClientEventHandler clientEventHandler)
        {
            this.clientEventHandler = clientEventHandler;
        }

        @Override
        public int onMessage(
            final WebSocketSession session,
            final ContentType contentType,
            final DirectBuffer buffer,
            final int offset,
            final int length)
        {
            clientEventHandler.onMessage(buffer, offset, length, contentType);
            return SendResult.OK;
        }

        @Override
        public void onCloseMessage(final short closeReason, final WebSocketSession session)
        {
            clientEventHandler.onConnectionClosed();
        }
    }

    private static final class HttpRequestDetails
    {
        private final String host;
        private final String uri;

        HttpRequestDetails(final String host, final String uri)
        {
            this.host = host;
            this.uri = uri;
        }
    }
}
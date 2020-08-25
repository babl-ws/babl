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
package com.aitusoftware.babl.ext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.agrona.CloseHelper;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

final class HealthCheckEndpoint implements Runnable
{
    private static final IdleStrategy IDLE_STRATEGY = new SleepingMillisIdleStrategy(10L);
    private static final int PORT = Integer.getInteger("babl.healthcheck.port", 8090);
    private static final byte[] HTTP_RESPONSE_OK_PREFIX = toBytes("HTTP/1.1 200 OK\r\n");
    private static final byte[] HTTP_RESPONSE_ERROR_PREFIX = toBytes("HTTP/1.1 500 Internal Server Error\r\n");
    private static final byte[] ERROR_HEADER_PREFIX = toBytes("X-babl-error: ");
    private static final byte[] ERROR_HEADER_SUFFIX = toBytes("\r\n");
    private static final byte[] HTTP_RESPONSE_SUFFIX = toBytes(
        "Content-Length: 0\r\n" +
            "Connection: close\r\n\r\n");

    private final BooleanSupplier healthCheck;
    private final Supplier<String> reasonSupplier;
    private final EpochClock clock;
    private final ByteBuffer sendBuffer = ByteBuffer.allocateDirect(512);

    private ServerSocketChannel serverSocketChannel;
    private SocketChannel channelToClose;
    private long nextBindAttemptMs = 0L;

    HealthCheckEndpoint(
        final BooleanSupplier healthCheck,
        final Supplier<String> reasonSupplier,
        final EpochClock clock)
    {
        this.healthCheck = healthCheck;
        this.reasonSupplier = reasonSupplier;
        this.clock = clock;
    }

    @Override
    public void run()
    {
        try
        {
            while (!Thread.currentThread().isInterrupted())
            {
                int workDone = tryAssignServerSocket();
                CloseHelper.quietClose(channelToClose);

                if (serverSocketChannel != null)
                {
                    workDone += tryProcessConnection();
                }
                IDLE_STRATEGY.idle(workDone);
            }
        }
        finally
        {
            cleanUp();
        }
    }

    private int tryProcessConnection()
    {
        try
        {
            final SocketChannel channel = serverSocketChannel.accept();
            if (channel != null)
            {
                channelToClose = channel;
                sendBuffer.clear();
                if (healthCheck.getAsBoolean())
                {
                    sendBuffer.put(HTTP_RESPONSE_OK_PREFIX);
                }
                else
                {
                    sendBuffer.put(HTTP_RESPONSE_ERROR_PREFIX);
                    sendBuffer.put(ERROR_HEADER_PREFIX);
                    sendBuffer.put(reasonSupplier.get().getBytes(StandardCharsets.UTF_8));
                    sendBuffer.put(ERROR_HEADER_SUFFIX);
                }
                sendBuffer.put(HTTP_RESPONSE_SUFFIX);
                sendBuffer.flip();
                channel.write(sendBuffer);
                return 1;
            }
        }
        catch (final IOException e)
        {
            e.printStackTrace(System.out);
        }
        return 0;
    }

    private void cleanUp()
    {
        CloseHelper.quietClose(serverSocketChannel);
    }

    private int tryAssignServerSocket()
    {
        if (serverSocketChannel == null && clock.time() > nextBindAttemptMs)
        {
            try
            {
                serverSocketChannel = ServerSocketChannel.open();
                serverSocketChannel.bind(new InetSocketAddress(PORT));
                serverSocketChannel.configureBlocking(false);
                return 1;
            }
            catch (final IOException e)
            {
                e.printStackTrace(System.out);
                nextBindAttemptMs = clock.time() + TimeUnit.SECONDS.toMillis(5L);
            }
        }
        return 0;
    }

    private static byte[] toBytes(final String input)
    {
        return input.getBytes(StandardCharsets.UTF_8);
    }
}
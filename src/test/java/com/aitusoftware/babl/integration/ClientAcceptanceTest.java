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
package com.aitusoftware.babl.integration;

import static com.google.common.truth.Truth.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.websocket.Client;
import com.aitusoftware.babl.websocket.ClientEventHandler;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;

class ClientAcceptanceTest
{
    private static final SleepingMillisIdleStrategy IDLE_STRATEGY = new SleepingMillisIdleStrategy(1L);
    private HttpServer httpServer;

    @BeforeEach
    void setUp() throws Exception
    {
        httpServer = Vertx.vertx().createHttpServer(new HttpServerOptions());
        httpServer.websocketHandler(new Handler<ServerWebSocket>()
        {
            @Override
            public void handle(final ServerWebSocket event)
            {
                event.frameHandler(new Handler<WebSocketFrame>()
                {
                    @Override
                    public void handle(final WebSocketFrame e2)
                    {
                        if (e2.isFinal())
                        {
                            event.writeTextMessage(e2.textData());
                        }
                    }
                });
            }
        });
        final CountDownLatch latch = new CountDownLatch(1);
        httpServer.listen(8192, new Handler<AsyncResult<HttpServer>>()
        {
            @Override
            public void handle(final AsyncResult<HttpServer> event)
            {
                latch.countDown();
            }
        });
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void shouldSendAndReceiveData() throws Exception
    {
        final int messageCount = 100;
        final CollectingClientEventHandler eventHandler = new CollectingClientEventHandler();
        final Client client = new Client(eventHandler);
        client.connect(new URL("http://localhost:" + httpServer.actualPort() + "/foo"));
        assertThat(client.upgradeConnection()).isTrue();
        final List<String> sentMessages = new ArrayList<>();
        for (int i = 0; i < messageCount; i++)
        {
            final String content = "hello world " + i;
            sentMessages.add(content);
            final DirectBuffer message = message(content);
            assertThat(client.offer(message, 0, message.capacity(), ContentType.TEXT)).isTrue();
        }
        final long timeoutAt = System.currentTimeMillis() + 5_000L;
        while (eventHandler.receivedMessages.size() < messageCount && System.currentTimeMillis() < timeoutAt)
        {
            IDLE_STRATEGY.idle(client.doWork());
        }
        for (int i = 0; i < messageCount; i++)
        {
            assertThat(eventHandler.receivedMessages.get(i)).isEqualTo(sentMessages.get(i));
        }
        assertThat(eventHandler.contentType).isEqualTo(ContentType.TEXT);
    }

    @AfterEach
    void tearDown()
    {
        httpServer.close();
    }

    private static DirectBuffer message(final String content)
    {
        return new UnsafeBuffer(content.getBytes(StandardCharsets.UTF_8));
    }

    private static final class CollectingClientEventHandler implements ClientEventHandler
    {
        private final List<String> receivedMessages = new ArrayList<>();
        private ContentType contentType;

        @Override
        public void onMessage(
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final ContentType contentType)
        {
            final byte[] dst = new byte[length];
            buffer.getBytes(offset, dst, 0, length);
            receivedMessages.add(new String(dst, StandardCharsets.UTF_8));
            this.contentType = contentType;
        }

        @Override
        public void onHeartbeatTimeout()
        {

        }

        @Override
        public void onConnectionClosed()
        {

        }
    }
}
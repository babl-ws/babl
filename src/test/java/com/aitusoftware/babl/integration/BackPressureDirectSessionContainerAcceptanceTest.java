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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.user.EchoApplication;

import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketFrame;

class BackPressureDirectSessionContainerAcceptanceTest
{
    private static final int MESSAGE_COUNT = 100;
    private final ServerHarness harness = new ServerHarness(new EchoApplication(true));
    private HttpClient client;
    @TempDir
    Path workingDir;

    @BeforeAll
    static void enableLogging()
    {
        System.setProperty(Logger.DEBUG_ENABLED_PROPERTY, "true");
    }

    @BeforeEach
    void setUp() throws IOException
    {
        harness.start(workingDir);
        client = Vertx.vertx().createHttpClient(new HttpClientOptions());
    }

    @AfterEach
    void tearDown()
    {
        CloseHelper.close(harness);
        client.close();
    }

    @Test
    void shouldHandleSingleClient() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        final List<String> messagesSent = new CopyOnWriteArrayList<>();
        final List<String> messagesReceived = new CopyOnWriteArrayList<>();
        client.webSocket(harness.serverPort(), "localhost", "/some-uri",
            new Handler<AsyncResult<WebSocket>>()
            {
                @Override
                public void handle(final AsyncResult<WebSocket> event)
                {
                    final WebSocket socket = event.result();
                    socket.frameHandler(new MessageHandler(messagesReceived, latch, MESSAGE_COUNT));
                    for (int i = 0; i < MESSAGE_COUNT; i++)
                    {
                        final String text = "payload-" + i;
                        socket.writeTextMessage(text);
                        messagesSent.add(text);
                    }
                }
            });

        assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        if (!messagesSent.equals(messagesReceived))
        {
            System.out.println(messagesSent);
            System.out.println(messagesReceived);
        }
        assertThat(messagesReceived).isEqualTo(messagesSent);
    }

    private static class MessageHandler implements Handler<WebSocketFrame>
    {
        private final List<String> messagesReceived;
        private final CountDownLatch latch;
        private final StringBuilder current = new StringBuilder();
        private final int expectedMessageCount;

        MessageHandler(
            final List<String> messagesReceived,
            final CountDownLatch latch,
            final int expectedMessageCount)
        {
            this.messagesReceived = messagesReceived;
            this.latch = latch;
            this.expectedMessageCount = expectedMessageCount;
        }

        @Override
        public void handle(final WebSocketFrame event)
        {
            if (event.isText() && !event.isClose())
            {
                current.append(event.textData());
                if (event.isFinal())
                {
                    messagesReceived.add(current.toString());
                    if (!current.toString().contains("payload"))
                    {
                        System.out.println("Unexpected: " + current.toString());
                    }
                    current.setLength(0);
                }
            }
            if (messagesReceived.size() == expectedMessageCount)
            {
                latch.countDown();
            }
        }
    }
}

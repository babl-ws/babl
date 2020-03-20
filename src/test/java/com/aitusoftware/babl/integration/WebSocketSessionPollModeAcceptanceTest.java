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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.aitusoftware.babl.user.EchoApplication;

import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
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

class WebSocketSessionPollModeAcceptanceTest
{
    private static final int MESSAGE_COUNT = 100;
    private static final int POLL_MODE_SESSION_LIMIT = 5;
    private final ServerHarness harness = new ServerHarness(new EchoApplication(false));
    private HttpClient client;
    @TempDir
    Path workingDir;

    @BeforeEach
    void setUp() throws IOException
    {
        harness.serverConfig().pollModeEnabled(true).pollModeSessionLimit(POLL_MODE_SESSION_LIMIT);
        harness.start(workingDir);
        client = Vertx.vertx().createHttpClient(new HttpClientOptions().setMaxPoolSize(50));
    }

    @AfterEach
    void tearDown()
    {
        CloseHelper.close(harness);
        client.close();
    }

    @Test
    void shouldHandleMultipleSessions() throws Exception
    {
        assertThatSessionsAreProcessed(POLL_MODE_SESSION_LIMIT - 1);
        assertThatSessionsAreProcessed(POLL_MODE_SESSION_LIMIT);
        assertThatSessionsAreProcessed(POLL_MODE_SESSION_LIMIT + 1);
    }

    private void assertThatSessionsAreProcessed(final int clientCount) throws Exception
    {
        final List<WebSocket> clientSockets = new ArrayList<>();
        for (int i = 0; i < clientCount; i++)
        {
            final CompletableFuture<WebSocket> webSocketFuture = new CompletableFuture<>();
            client.webSocket(harness.serverPort(), "localhost", "/some-uri",
                new Handler<AsyncResult<WebSocket>>()
                {
                    @Override
                    public void handle(final AsyncResult<WebSocket> event)
                    {
                        final WebSocket socket = event.result();
                        webSocketFuture.complete(socket);
                    }
                });
            clientSockets.add(webSocketFuture.get(5, TimeUnit.SECONDS));
        }


        final List<ClientData> clientDataList = new ArrayList<>(clientCount);
        for (final WebSocket clientSocket : clientSockets)
        {
            final ClientData clientData = new ClientData(clientSocket);
            clientDataList.add(clientData);
            clientSocket.frameHandler(clientData);
            for (int i = 0; i < MESSAGE_COUNT; i++)
            {
                final String text = "payload-" + i;
                clientSocket.writeTextMessage(text);
                clientData.messagesSent.add(text);
            }
        }

        for (final ClientData clientData : clientDataList)
        {
            assertThat(clientData.latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(clientData.messagesReceived).isEqualTo(clientData.messagesSent);
            CloseHelper.close(clientData);
        }
    }

    private static final class ClientData implements Handler<WebSocketFrame>, AutoCloseable
    {
        private final CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);
        private final List<String> messagesSent = new ArrayList<>(MESSAGE_COUNT);
        private final List<String> messagesReceived = new ArrayList<>(MESSAGE_COUNT);
        private final WebSocket clientSocket;

        ClientData(final WebSocket clientSocket)
        {
            this.clientSocket = clientSocket;
        }

        @Override
        public void handle(final WebSocketFrame event)
        {
            if (event.isFinal())
            {
                messagesReceived.add(event.textData());
                latch.countDown();
            }
        }

        @Override
        public void close()
        {
            clientSocket.close();
        }
    }
}

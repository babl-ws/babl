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
import java.util.concurrent.TimeUnit;

import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.user.EchoApplication;
import com.aitusoftware.babl.websocket.Constants;

import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
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

class MultipleWebSocketSessionDirectSessionContainerAcceptanceTest
{
    private static final int CLIENT_COUNT = 37;
    private static final int MESSAGE_COUNT = 100;

    private final EchoApplication application = new EchoApplication(false);
    private final ServerHarness harness = new ServerHarness(application);
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
        final List<WebSocket> clientSockets = new ArrayList<>();
        for (int i = 0; i < CLIENT_COUNT; i++)
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


        final List<ClientData> clientDataList = new ArrayList<>(CLIENT_COUNT);
        for (final WebSocket clientSocket : clientSockets)
        {
            final ClientData clientData = new ClientData(MESSAGE_COUNT);
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
        }

        for (final WebSocket clientSocket : clientSockets)
        {
            clientSocket.close(Constants.CLOSE_REASON_NORMAL);
        }

        // extra session created by server-up check in harness
        assertThat(application.getSessionOpenedCount()).isEqualTo(CLIENT_COUNT);

        Awaitility.await().atMost(15, TimeUnit.SECONDS).pollInSameThread()
            .until(() -> application.getSessionClosedCount() == CLIENT_COUNT + 1);
    }
}
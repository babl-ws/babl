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
import java.util.concurrent.locks.LockSupport;

import com.aitusoftware.babl.config.DeploymentMode;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.user.EchoApplication;
import com.aitusoftware.babl.websocket.Constants;

import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocket;

@Disabled
class SoakTest
{
    private static final int ACTIVE_CLIENT_COUNT = 1200;
    private static final int MESSAGE_COUNT = 100;

    private final EchoApplication application = new EchoApplication(false);
    private final ServerHarness harness = new ServerHarness(application);
    private HttpClient client;
    @TempDir
    Path workingDir;
    private Path serverBaseDir;

    @BeforeAll
    static void disableLogging()
    {
        System.setProperty(Logger.DEBUG_ENABLED_PROPERTY, "false");
    }

    @BeforeEach
    void setUp() throws IOException
    {
        harness.sessionContainerConfig().deploymentMode(DeploymentMode.DETACHED);
        harness.sessionContainerConfig().sessionContainerInstanceCount(1);
        harness.sessionContainerConfig().activeSessionLimit(2000);
        harness.proxyConfig()
            .launchMediaDriver(true)
            .mediaDriverDir(workingDir.resolve("driver").toString());
        serverBaseDir = workingDir.resolve("server");

        harness.start(serverBaseDir);
    }

    @AfterEach
    void tearDown()
    {
        CloseHelper.close(harness);
        client.close();
    }

    @Test
    void longRunningTest() throws Exception
    {
        for (int i = 0; i < 3; i++)
        {
            shouldHandleMultipleSessions(i + 1);
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1L));
        }
    }

    private void shouldHandleMultipleSessions(final int iteration) throws Exception
    {
        assignClient();
        final List<WebSocket> clientSockets = new ArrayList<>();
        for (int i = 0; i < ACTIVE_CLIENT_COUNT; i++)
        {
            final CompletableFuture<WebSocket> webSocketFuture = new CompletableFuture<>();
            client.webSocket(harness.serverPort(), "localhost", "/some-uri",
                new Handler<AsyncResult<WebSocket>>()
                {
                    @Override
                    public void handle(final AsyncResult<WebSocket> event)
                    {
                        if (event.succeeded())
                        {
                            final WebSocket socket = event.result();
                            webSocketFuture.complete(socket);
                        }
                        else
                        {
                            webSocketFuture.completeExceptionally(event.cause());
                        }
                    }
                });
            clientSockets.add(webSocketFuture.get(35, TimeUnit.SECONDS));
        }

        final List<ClientData> clientDataList = new ArrayList<>(ACTIVE_CLIENT_COUNT);
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
            assertThat(clientData.latch.await(50, TimeUnit.SECONDS)).isTrue();
            assertThat(clientData.messagesReceived).isEqualTo(clientData.messagesSent);
        }

        for (final WebSocket clientSocket : clientSockets)
        {
            clientSocket.close(Constants.CLOSE_REASON_NORMAL);
        }

        assertThat(application.getSessionOpenedCount()).isEqualTo(ACTIVE_CLIENT_COUNT * iteration);
    }

    private void assignClient()
    {
        if (client != null)
        {
            client.close();
        }
        client = Vertx.vertx().createHttpClient(new HttpClientOptions().setMaxPoolSize(ACTIVE_CLIENT_COUNT));
    }
}

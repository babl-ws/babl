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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.aitusoftware.babl.config.DeploymentMode;
import com.aitusoftware.babl.monitoring.SessionContainerStatisticsPrinter;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.user.EchoApplication;
import com.aitusoftware.babl.websocket.Constants;
import com.aitusoftware.babl.websocket.Session;

import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
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

class MultipleWebSocketSessionDetachedSessionContainerAcceptanceTest
{
    private static final int CLIENT_COUNT = 37;
    private static final int MESSAGE_COUNT = 100;
    private static final int SERVER_INSTANCE_COUNT = 3;

    private final EchoApplication application = new EchoApplication(false);
    private final ServerHarness harness = new ServerHarness(application);
    private final CountingAgent additionalWork =
        new CountingAgent(application.getSessionCache(), true);
    private HttpClient client;
    @TempDir
    Path workingDir;
    private Path serverBaseDir;

    @BeforeEach
    void setUp() throws IOException
    {
        harness.serverConfig().deploymentMode(DeploymentMode.DETACHED);
        harness.serverConfig().sessionContainerInstanceCount(SERVER_INSTANCE_COUNT);
        harness.applicationConfig().additionalWork(additionalWork);
        harness.proxyConfig()
            .launchMediaDriver(true)
            .mediaDriverDir(workingDir.resolve("driver").toString());
        serverBaseDir = workingDir.resolve("server");
        harness.start(serverBaseDir);
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
            assertThat(clientData.latch.await(20, TimeUnit.SECONDS)).isTrue();
            assertThat(clientData.messagesReceived).isEqualTo(clientData.messagesSent);
        }

        final MutableInteger totalSessionCount = new MutableInteger();
        for (int i = 0; i < SERVER_INSTANCE_COUNT; i++)
        {
            final Path serverInstanceDir = serverBaseDir.resolve(Integer.toString(i));
            SessionContainerStatisticsPrinter.readServerStatistics(serverInstanceDir,
                (timestamp, bytesRead, bytesWritten,
                activeSessionCount, receiveBackPressureEvents,
                invalidOpCodeEvents, eventLoopDurationMs,
                proxyBackPressureEvents, proxyBackPressured) ->
                totalSessionCount.set(totalSessionCount.get() + activeSessionCount));
        }

        assertThat(totalSessionCount.get()).isEqualTo(CLIENT_COUNT);

        for (final WebSocket clientSocket : clientSockets)
        {
            clientSocket.close(Constants.CLOSE_REASON_NORMAL);
        }

        // extra session created by server-up check in harness
        assertThat(application.getSessionOpenedCount()).isEqualTo(CLIENT_COUNT);

        Awaitility.await().atMost(15, TimeUnit.SECONDS).pollInSameThread()
            .until(() -> application.getSessionClosedCount() == CLIENT_COUNT + 1);

        assertThat(additionalWork.invocationCount.get()).isNotEqualTo(0);
        assertThat(clientDataList.stream().anyMatch(data -> !data.otherMessagesReceived.isEmpty())).isTrue();
    }

    private static final class CountingAgent implements Agent
    {
        private final AtomicLong invocationCount = new AtomicLong();
        private final Long2ObjectHashMap<Session> sessionCache;
        private final Long2ObjectHashMap<Boolean> sent = new Long2ObjectHashMap<>();
        private final DirectBuffer payload = new UnsafeBuffer("Hello".getBytes(StandardCharsets.UTF_8));
        private final boolean sendMessages;

        CountingAgent(
            final Long2ObjectHashMap<Session> sessionCache,
            final boolean sendMessages)
        {
            this.sessionCache = sessionCache;
            this.sendMessages = sendMessages;
        }

        @Override
        public int doWork()
        {
            if (sendMessages)
            {
                final Long2ObjectHashMap<Session>.KeySet keySet = sessionCache.keySet();
                final Long2ObjectHashMap<Session>.KeyIterator keyIterator = keySet.iterator();
                while (keyIterator.hasNext())
                {

                    final long sessionId = keyIterator.nextLong();
                    if (!sent.containsKey(sessionId))
                    {
                        sessionCache.get(sessionId).send(ContentType.TEXT, payload, 0, payload.capacity());
                        sent.put(sessionId, Boolean.TRUE);
                    }
                }
            }
            invocationCount.incrementAndGet();
            return 0;
        }

        @Override
        public String roleName()
        {
            return "additional";
        }
    }
}
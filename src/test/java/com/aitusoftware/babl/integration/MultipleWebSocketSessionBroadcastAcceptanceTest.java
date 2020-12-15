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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.aitusoftware.babl.config.DeploymentMode;
import com.aitusoftware.babl.user.Application;
import com.aitusoftware.babl.user.BroadcastSource;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.websocket.Constants;
import com.aitusoftware.babl.websocket.DisconnectReason;
import com.aitusoftware.babl.websocket.SendResult;
import com.aitusoftware.babl.websocket.Session;
import com.aitusoftware.babl.websocket.broadcast.AbstractMessageTransformer;
import com.aitusoftware.babl.websocket.broadcast.Broadcast;
import com.aitusoftware.babl.websocket.broadcast.TransformResult;

import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.LongHashSet;
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
import io.vertx.core.http.WebSocketFrame;

class MultipleWebSocketSessionBroadcastAcceptanceTest
{
    private static final int CLIENT_COUNT = 3;
    private static final int SERVER_INSTANCE_COUNT = 2;
    private static final String TOPIC_ONE_MSG = "TOPIC_ONE";
    private static final String TOPIC_TWO_MSG = "TOPIC_TWO";
    private static final String MULTI_TOPIC_MSG = "MULTI_TOPIC";
    private static final int TOPIC_ONE = 1337;
    private static final int TOPIC_TWO = 999;
    private static final int[] TOPIC_IDS = new int[] {TOPIC_ONE, TOPIC_TWO};

    private final BroadcastApplication application = new BroadcastApplication();
    private final ServerHarness harness = new ServerHarness(application);
    private HttpClient client;
    @TempDir
    Path workingDir;
    private Path serverBaseDir;

    @BeforeEach
    void setUp() throws IOException
    {
        harness.sessionContainerConfig().messageTransformerFactory(id -> new SuffixingMessageTransformer());
        harness.sessionContainerConfig().deploymentMode(DeploymentMode.DETACHED);
        harness.sessionContainerConfig().sessionContainerInstanceCount(SERVER_INSTANCE_COUNT);
        harness.applicationConfig().additionalWork(application);
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
    void shouldBroadcastOnMultipleTopics() throws Exception
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


        final List<ClientHandler> clientDataList = new ArrayList<>(CLIENT_COUNT);
        for (final WebSocket clientSocket : clientSockets)
        {
            final ClientHandler clientHandler = new ClientHandler();
            clientDataList.add(clientHandler);
            clientSocket.frameHandler(clientHandler);
        }

        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .until(() ->
            {
                return
                    clientDataList.stream().filter(ClientHandler::hasSeveralTopicOneMessages).count() == CLIENT_COUNT &&
                    clientDataList.stream().filter(ClientHandler::hasSeveralTopicTwoMessages).count() ==
                        application.evenNumberedSessionCount.get() &&
                    clientDataList.stream().filter(ClientHandler::hasSeveralMultiTopicMessages).count() ==
                        application.evenNumberedSessionCount.get();
            });

        for (final WebSocket clientSocket : clientSockets)
        {
            clientSocket.close(Constants.CLOSE_REASON_NORMAL);
        }
    }

    private static final class SuffixingMessageTransformer extends AbstractMessageTransformer
    {
        private final ExpandableArrayBuffer suffixBuffer = new ExpandableArrayBuffer();

        @Override
        protected boolean doTransform(
            final int topicId,
            final DirectBuffer input,
            final int offset,
            final int length,
            final TransformResult result)
        {
            suffixBuffer.putBytes(0, input, offset, length);
            final byte[] suffix = ("-" + topicId).getBytes(StandardCharsets.UTF_8);
            suffixBuffer.putBytes(length, suffix);
            result.set(suffixBuffer, 0, length + suffix.length);
            return true;
        }
    }

    private static final class ClientHandler implements Handler<WebSocketFrame>
    {
        private final List<String> receivedMessages = new CopyOnWriteArrayList<>();

        @Override
        public void handle(final WebSocketFrame event)
        {
            if (event.isFinal() && event.isText())
            {
                receivedMessages.add(event.textData());
            }
        }

        private boolean hasSeveralMultiTopicMessages()
        {
            return hasSeveralMessages(MULTI_TOPIC_MSG + "-" + TOPIC_ONE) &&
                    hasSeveralMessages(MULTI_TOPIC_MSG + "-" + TOPIC_TWO);
        }

        private boolean hasSeveralTopicOneMessages()
        {
            return hasSeveralMessages(TOPIC_ONE_MSG + "-" + TOPIC_ONE);
        }

        private boolean hasSeveralTopicTwoMessages()
        {
            return hasSeveralMessages(TOPIC_TWO_MSG + "-" + TOPIC_TWO);
        }

        private boolean hasSeveralMessages(final String filter)
        {
            return receivedMessages.stream().filter(msg -> msg.contentEquals(filter)).count() > 1;
        }
    }

    private static final class BroadcastApplication implements Application, BroadcastSource, Agent
    {
        private static final long PUBLICATION_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(100);

        private final LongHashSet sessionIds = new LongHashSet();
        private final AtomicLong evenNumberedSessionCount = new AtomicLong();
        private final UnsafeBuffer topicOneMsg = new UnsafeBuffer(TOPIC_ONE_MSG.getBytes(StandardCharsets.UTF_8));
        private final UnsafeBuffer topicTwoMsg = new UnsafeBuffer(TOPIC_TWO_MSG.getBytes(StandardCharsets.UTF_8));
        private final UnsafeBuffer multiTopicMsg = new UnsafeBuffer(MULTI_TOPIC_MSG.getBytes(StandardCharsets.UTF_8));
        private Broadcast broadcast;
        private boolean topicsCreated = false;
        private long lastPublicationNs;

        @Override
        public int onSessionConnected(final Session session)
        {
            sessionIds.add(session.id());
            return SendResult.OK;
        }

        @Override
        public int onSessionDisconnected(final Session session, final DisconnectReason reason)
        {
            return SendResult.OK;
        }

        @Override
        public int onSessionMessage(
            final Session session,
            final ContentType contentType,
            final DirectBuffer msg,
            final int offset,
            final int length)
        {
            return SendResult.OK;
        }

        @Override
        public void setBroadcast(final Broadcast broadcast)
        {
            this.broadcast = broadcast;
        }

        @Override
        public int doWork()
        {
            if (sessionIds.size() == CLIENT_COUNT)
            {
                if (!topicsCreated)
                {
                    broadcast.createTopic(TOPIC_ONE);
                    broadcast.createTopic(TOPIC_TWO);
                    for (final Long sessionId : sessionIds)
                    {
                        broadcast.addToTopic(TOPIC_ONE, sessionId);
                        if ((sessionId & 1) == 0)
                        {
                            broadcast.addToTopic(TOPIC_TWO, sessionId);
                            evenNumberedSessionCount.incrementAndGet();
                        }
                    }
                    topicsCreated = true;
                }

                if (System.nanoTime() - lastPublicationNs > PUBLICATION_INTERVAL_NS)
                {
                    broadcast.sendToTopic(TOPIC_ONE, ContentType.TEXT, topicOneMsg, 0, topicOneMsg.capacity());
                    broadcast.sendToTopic(TOPIC_TWO, ContentType.TEXT, topicTwoMsg, 0, topicTwoMsg.capacity());
                    broadcast.sendToTopics(TOPIC_IDS, TOPIC_IDS.length, ContentType.TEXT,
                        multiTopicMsg, 0, multiTopicMsg.capacity());

                    lastPublicationNs = System.nanoTime();
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public String roleName()
        {
            return "broadcast-app";
        }
    }
}
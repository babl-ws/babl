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

import static com.aitusoftware.babl.websocket.Constants.CLOSE_REASON_PROTOCOL_ERROR;
import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.user.EchoApplication;
import com.aitusoftware.babl.websocket.Client;
import com.aitusoftware.babl.websocket.ClientEventHandler;
import com.aitusoftware.babl.websocket.ConnectionValidator;
import com.aitusoftware.babl.websocket.ValidationResult;
import com.aitusoftware.babl.websocket.ValidationResultPublisher;

import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.http.WebSocketFrame;

class SingleWebSocketSessionDirectSessionContainerAcceptanceTest
{
    private static final int MESSAGE_COUNT = 100;
    private static final String REQUEST_URI = "/";
    private static final String HOST = "localhost";

    private final EchoApplication application = new EchoApplication(false);
    private final ServerHarness harness = new ServerHarness(application);
    private final TestConnectionValidator testConnectionValidator = new TestConnectionValidator();

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
        harness.serverConfig().connectionValidator(testConnectionValidator);
        harness.serverConfig().validationTimeoutNanos(TimeUnit.MILLISECONDS.toNanos(500L));
        harness.sessionConfig().maxBufferSize(1 << 20);
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
    void shouldHandleUpgradeFailure() throws IOException
    {
        final NoOpClientEventHandler clientEventHandler = new NoOpClientEventHandler();
        final Client client = new Client(clientEventHandler);

        client.connect(new URL("http://" + HOST + ":" + harness.serverPort()));
        assertThat(client.upgradeConnection()).isFalse();
        final DirectBuffer payload = new UnsafeBuffer("hello".getBytes(StandardCharsets.UTF_8));
        Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.SECONDS).until(() ->
        {
            client.offer(payload, 0, 5, ContentType.TEXT);
            try
            {
                client.doWork();
            }
            catch (final IOException e)
            {
                // ignore
            }
            return clientEventHandler.latch.getCount() == 0;
        });
    }

    @Test
    void shouldEchoMediumAndLargePayload() throws InterruptedException
    {
        sendSingleMessage(126);
        sendSingleMessage(65536);
        sendSingleMessage(65537);
    }

    @Test
    void shouldSendCloseResponseMessage() throws Exception
    {
        final WebSocket webSocket = getWebSocket();
        final CountDownLatch latch = new CountDownLatch(1);
        webSocket.closeHandler(null_ -> latch.countDown());

        webSocket.close(CLOSE_REASON_PROTOCOL_ERROR, "test");

        latch.await(2, TimeUnit.SECONDS);

        assertThat(webSocket.closeStatusCode()).isEqualTo(CLOSE_REASON_PROTOCOL_ERROR);
    }

    @Test
    void shouldRespondToPings() throws Exception
    {
        final WebSocket webSocket = getWebSocket();
        final CompletableFuture<String> pongPayload = new CompletableFuture<>();
        webSocket.pongHandler(new Handler<Buffer>()
        {
            @Override
            public void handle(final Buffer event)
            {
                pongPayload.complete(event.toString(StandardCharsets.UTF_8));
            }
        });
        final BufferImpl pingData = new BufferImpl();
        final String pingPayload = Long.toString(System.nanoTime());
        pingData.appendString(pingPayload);
        webSocket.writePing(pingData);
        assertThat(pongPayload.get(5, TimeUnit.SECONDS)).isEqualTo(pingPayload);
    }

    @Test
    void shouldHandleSingleClient() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        final List<String> messagesSent = new ArrayList<>();
        final List<String> messagesReceived = new ArrayList<>();
        client.webSocket(harness.serverPort(), HOST, REQUEST_URI,
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

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        System.out.println(messagesReceived);
        assertThat(messagesReceived).isEqualTo(messagesSent);
    }

    @Test
    void shouldPropagateUpgradeRequestHeaders() throws Exception
    {
        final WebSocketConnectOptions connectOptions = new WebSocketConnectOptions()
            .setHost(HOST)
            .setPort(harness.serverPort())
            .setURI(REQUEST_URI)
            .addHeader("custom-header-0", "value-0")
            .addHeader("X-custom-header-1", "VaLuE-1");
        final CountDownLatch latch = new CountDownLatch(1);
        client.webSocket(connectOptions,
            new Handler<AsyncResult<WebSocket>>()
            {
                @Override
                public void handle(final AsyncResult<WebSocket> event)
                {
                    final WebSocket socket = event.result();
                    socket.frameHandler(new MessageHandler(new ArrayList<>(), latch, 1));
                    socket.writeTextMessage("payload");
                }
            });
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        final Map<String, String> sessionHttpHeaders = testConnectionValidator.capturedHttpHeaders.get(2L);
        assertThat(sessionHttpHeaders).containsEntry("custom-header-0", "value-0");
        assertThat(sessionHttpHeaders).containsEntry("x-custom-header-1", "VaLuE-1");
    }

    @Test
    void validationFailureShouldCauseDisconnect() throws Exception
    {
        testConnectionValidator.validationResultCode.set(ValidationResult.VALIDATION_FAILED);

        final CountDownLatch latch = new CountDownLatch(1);
        client.webSocket(harness.serverPort(), HOST, REQUEST_URI,
            new Handler<AsyncResult<WebSocket>>()
            {
                @Override
                public void handle(final AsyncResult<WebSocket> event)
                {
                    final WebSocket socket = event.result();
                    socket.frameHandler(new ExpectCloseHandler(latch, ValidationResult.VALIDATION_FAILED));
                    socket.writeTextMessage("payload");
                }
            });
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private static final class TestConnectionValidator implements ConnectionValidator
    {
        private final Map<Long, Map<String, String>> capturedHttpHeaders = new HashMap<>();
        private final AtomicInteger validationResultCode = new AtomicInteger(ValidationResult.CONNECTION_VALID);

        @Override
        public void validateConnection(
            final ValidationResult validationResult,
            final Consumer<BiConsumer<CharSequence, CharSequence>> headerProvider,
            final ValidationResultPublisher validationResultPublisher)
        {
            final Map<String, String> headers = new HashMap<>();
            headerProvider.accept((k, v) -> headers.put(k.toString(), v.toString()));
            capturedHttpHeaders.put(validationResult.sessionId(), headers);
            if (validationResultCode.get() == ValidationResult.CONNECTION_VALID)
            {
                validationResult.validationSuccess();
            }
            else
            {
                validationResult.validationFailure((short)validationResultCode.get());
            }
            validationResultPublisher.publishResult(validationResult);
        }
    }

    private WebSocket getWebSocket() throws InterruptedException, java.util.concurrent.ExecutionException,
        java.util.concurrent.TimeoutException
    {
        final CompletableFuture<WebSocket> socketFuture = new CompletableFuture<>();
        client.webSocket(harness.serverPort(), HOST, REQUEST_URI,
            new Handler<AsyncResult<WebSocket>>()
            {
                @Override
                public void handle(final AsyncResult<WebSocket> event)
                {
                    final WebSocket socket = event.result();
                    socketFuture.complete(socket);
                }
            });

        return socketFuture.get(1, TimeUnit.SECONDS);
    }

    private void sendSingleMessage(final int payloadSize) throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        final List<String> messagesSent = new ArrayList<>();
        final List<String> messagesReceived = new ArrayList<>();
        client.webSocket(harness.serverPort(), HOST, REQUEST_URI,
            new Handler<AsyncResult<WebSocket>>()
            {
                @Override
                public void handle(final AsyncResult<WebSocket> event)
                {
                    final WebSocket socket = event.result();
                    Optional.ofNullable(event.cause()).ifPresent(Throwable::printStackTrace);
                    socket.frameHandler(new MessageHandler(messagesReceived, latch, 1));
                    final StringBuilder payload = new StringBuilder();
                    for (int i = 0; i < payloadSize; i++)
                    {
                        payload.append((char)('a' + (i % 26)));
                    }
                    socket.writeTextMessage(payload.toString());
                    System.out.printf("%s Sent payload of %dB%n", Instant.now(), payloadSize);
                    messagesSent.add(payload.toString());
                }
            });

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        final String expected = messagesSent.get(0);
        final String actual = messagesReceived.get(0);
        for (int i = 0; i < actual.length(); i++)
        {
            if (actual.charAt(i) != expected.charAt(i))
            {
                System.out.println("Differ at " + i);
                break;
            }
        }
        assertThat(actual).isEqualTo(expected);
    }

    private static final class ExpectCloseHandler implements Handler<WebSocketFrame>
    {
        private final CountDownLatch latch;
        private final short expectedCloseReason;

        private ExpectCloseHandler(final CountDownLatch latch, final short expectedCloseReason)
        {
            this.latch = latch;
            this.expectedCloseReason = expectedCloseReason;
        }

        @Override
        public void handle(final WebSocketFrame event)
        {
            if (event.isClose() && event.closeStatusCode() == expectedCloseReason)
            {
                latch.countDown();
            }
        }
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
            current.append(event.textData());
            if (event.isFinal())
            {
                messagesReceived.add(current.toString());
                current.setLength(0);
            }
            if (messagesReceived.size() == expectedMessageCount)
            {
                latch.countDown();
            }
        }
    }

    private static class NoOpClientEventHandler implements ClientEventHandler
    {
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onMessage(
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final ContentType contentType)
        {

        }

        @Override
        public void onHeartbeatTimeout()
        {

        }

        @Override
        public void onConnectionClosed()
        {
            latch.countDown();
        }
    }
}

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

import static com.aitusoftware.babl.websocket.FrameUtil.assertWebSocketFrame;
import static com.aitusoftware.babl.websocket.SendResult.BACK_PRESSURE;
import static com.aitusoftware.babl.websocket.SendResult.OK;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.aitusoftware.babl.config.SessionConfig;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.monitoring.SessionContainerStatistics;
import com.aitusoftware.babl.monitoring.SessionStatistics;
import com.aitusoftware.babl.pool.BufferPool;
import com.aitusoftware.babl.user.Application;
import com.aitusoftware.babl.user.ContentType;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@SuppressWarnings("unchecked")
class WebSocketSessionTest
{
    private static final long SESSION_ID = 17L;
    private static final int INITIAL_SOCKET_BUFFER_CAPACITY = 1024;
    private static final int MAX_SOCKET_BUFFER_SIZE = 4 * INITIAL_SOCKET_BUFFER_CAPACITY;
    private static final int INTERNAL_BUFFER_INITIAL_CAPACITY = 1024;
    private static final int INTERNAL_BUFFER_MAX_CAPACITY = 4096;
    private static final long PING_INTERVAL_MS = 5_000L;
    private static final long PONG_RESPONSE_TIMEOUT_MS = 10_000L;
    private static final long BASE_TIME_MS = 1234567890123L;

    private final BufferPool bufferPool = new BufferPool();
    private final SocketChannel channel = mock(SocketChannel.class);
    private final SessionStatistics sessionStatistics = mock(SessionStatistics.class);
    private final SessionContainerStatistics sessionContainerStatistics = mock(SessionContainerStatistics.class);
    private final SessionDataListener sessionDataListener = mock(SessionDataListener.class);
    private final ConnectionUpgrade connectionUpgrade = mock(ConnectionUpgrade.class);
    private final Application application = mock(Application.class);
    private final SessionConfig sessionConfig = new SessionConfig()
        .sessionDecodeBufferSize(INTERNAL_BUFFER_INITIAL_CAPACITY)
        .maxSessionDecodeBufferSize(INTERNAL_BUFFER_MAX_CAPACITY)
        .sendBufferSize(INITIAL_SOCKET_BUFFER_CAPACITY)
        .maxBufferSize(MAX_SOCKET_BUFFER_SIZE)
        .maxWebSocketFrameLength(65536)
        .pingIntervalNanos(TimeUnit.MILLISECONDS.toNanos(PING_INTERVAL_MS))
        .pongResponseTimeoutNanos(TimeUnit.MILLISECONDS.toNanos(PONG_RESPONSE_TIMEOUT_MS));
    private final EpochClock clock = mock(EpochClock.class, "static");
    private final FrameEncoder frameEncoder = new FrameEncoder(
        bufferPool, sessionDataListener, sessionConfig, sessionContainerStatistics);
    private final PingAgent pingAgent = new PingAgent(
        frameEncoder, clock, PING_INTERVAL_MS, PONG_RESPONSE_TIMEOUT_MS, sessionDataListener);
    private final WebSocketSession session = new WebSocketSession(
        sessionDataListener,
        new FrameDecoder(new MessageDispatcher(application), sessionConfig, bufferPool,
        pingAgent, true, sessionContainerStatistics),
        frameEncoder,
        sessionConfig, bufferPool, application, pingAgent, clock, sessionStatistics, sessionContainerStatistics);
    private final UnsafeBuffer applicationBuffer = new UnsafeBuffer(new byte[512]);
    private final SelectionKey selectionKey = mock(SelectionKey.class);

    @BeforeAll
    static void enableLogging()
    {
        System.setProperty(Logger.DEBUG_ENABLED_PROPERTY, "true");
    }

    @BeforeEach
    void setUp()
    {
        given(clock.time()).willReturn(BASE_TIME_MS);
        session.init(SESSION_ID, connectionUpgrade, cu -> {}, BASE_TIME_MS, channel, channel);
        session.selectionKey(selectionKey);
    }

    @Test
    void shouldDisconnectAfterClosingPhaseTimeOut() throws IOException
    {
        final EpochClock advancingClock = mock(EpochClock.class, "advancing");
        given(advancingClock.time()).willReturn(BASE_TIME_MS, BASE_TIME_MS + 6_000L);
        final WebSocketSession closingSession = new WebSocketSession(
            sessionDataListener,
            new FrameDecoder(
            new MessageDispatcher(application), sessionConfig, bufferPool,
            pingAgent, true, sessionContainerStatistics),
            frameEncoder, sessionConfig, bufferPool, application, pingAgent,
            advancingClock, sessionStatistics, sessionContainerStatistics);
        closingSession.init(SESSION_ID, connectionUpgrade, cu -> {}, BASE_TIME_MS, channel, channel);
        closingSession.selectionKey(selectionKey);
        closingSession.onCloseMessage((short)1);
        closingSession.doSendWork();

        verify(sessionDataListener).sessionClosed();
    }

    @Test
    void shouldCancelSelectionKeyOnClose()
    {
        session.onSessionClosed();
        verify(selectionKey).cancel();
    }

    @Test
    void shouldSendPingMessageAfterConfiguredInterval() throws IOException
    {
        given(clock.time()).willReturn(
            BASE_TIME_MS + PING_INTERVAL_MS + 1,
            BASE_TIME_MS + PING_INTERVAL_MS + 1 + PONG_RESPONSE_TIMEOUT_MS + 1);
        provideSocketData(handshake());
        upgradeConnection();
        session.validated();

        // time = BASE_TIME_MS + PING_INTERVAL_MS + 1 <- send ping
        session.doAdminWork();
        // time = ping send time + PONG_RESPONSE_TIMEOUT_MS + 1 <- notify application that session is closed
        session.doReceiveWork();

        verify(application).onSessionDisconnected(session, DisconnectReason.HEARTBEAT_TIMEOUT);
    }

    @Test
    void shouldNotSendIfHandshakeNotComplete()
    {
        assertThat(session.send(ContentType.BINARY, applicationBuffer, 0, 10))
            .isEqualTo(SendResult.NOT_CONNECTED);
    }

    @Test
    void shouldSendSmallMessage() throws IOException
    {
        provideSocketData(handshake());
        upgradeConnection();
        final ByteBuffer captureBuffer = ByteBuffer.allocate(INITIAL_SOCKET_BUFFER_CAPACITY);
        doAnswer(invocation ->
        {
            final ByteBuffer content = invocation.getArgument(0);
            captureBuffer.put(content);
            captureBuffer.flip();
            return captureBuffer.remaining();
        }).when(channel).write(any(ByteBuffer.class));


        session.doReceiveWork();
        session.validated();
        assertThat(session.send(ContentType.BINARY, applicationBuffer, 0, 10))
            .isEqualTo(OK);

        session.doSendWork();

        final InOrder order = inOrder(sessionDataListener);
        order.verify(sessionDataListener, times(2)).sendDataAvailable();
        order.verify(sessionDataListener).sendDataProcessed();
        assertWebSocketFrame(0, 10, captureBuffer);
    }

    @Test
    void shouldReceiveSingleFrame() throws IOException
    {
        provideSocketData(handshake(), buffer ->
        {
            FrameUtil.writeWebSocketFrame(new byte[64], buffer);
        });
        upgradeConnection();

        session.doReceiveWork();
        session.validated();
        session.doReceiveWork();

        final InOrder order = Mockito.inOrder(sessionDataListener, connectionUpgrade, application);
        order.verify(sessionDataListener).receiveDataAvailable();
        order.verify(connectionUpgrade).handleUpgrade(any(), any());
        order.verify(sessionDataListener).receiveDataProcessed();
        order.verify(sessionDataListener).receiveDataAvailable();
        order.verify(application).onSessionMessage(
            same(session), eq(ContentType.BINARY), any(DirectBuffer.class), eq(0), eq(64));
        order.verify(sessionDataListener).receiveDataProcessed();
    }

    @Test
    void shouldReceiveMultipleFrames() throws IOException
    {
        provideSocketData(handshake(), buffer ->
        {
            FrameUtil.writeWebSocketFrame(new byte[64], buffer);
            FrameUtil.writeWebSocketFrame(new byte[31], buffer);
        });
        upgradeConnection();

        session.doReceiveWork();
        session.validated();
        session.doReceiveWork();

        final InOrder order = Mockito.inOrder(sessionDataListener, connectionUpgrade, application);
        order.verify(sessionDataListener).receiveDataAvailable();
        order.verify(connectionUpgrade).handleUpgrade(any(), any());
        order.verify(sessionDataListener).receiveDataProcessed();
        order.verify(sessionDataListener).receiveDataAvailable();
        order.verify(application).onSessionMessage(
            same(session), eq(ContentType.BINARY), any(DirectBuffer.class), eq(0), eq(64));
        order.verify(application).onSessionMessage(
            same(session), eq(ContentType.BINARY), any(DirectBuffer.class), eq(0), eq(31));
        order.verify(sessionDataListener).receiveDataProcessed();
    }

    @Test
    void shouldBufferFramesBetweenSocketReads() throws IOException
    {
        provideSocketData(handshake(),
            buffer ->
            {
                FrameUtil.writeWebSocketFrame(new byte[64], buffer);
                FrameUtil.writeWebSocketFrame(new byte[31], 20, buffer);
            }, buffer ->
            {
                buffer.put(new byte[31 - 20]);
            });

        upgradeConnection();

        session.doReceiveWork();
        session.validated();
        session.doReceiveWork();
        session.doReceiveWork();

        final InOrder order = Mockito.inOrder(sessionDataListener, connectionUpgrade, application);
        order.verify(sessionDataListener).receiveDataAvailable();
        order.verify(connectionUpgrade).handleUpgrade(any(), any());
        order.verify(sessionDataListener).receiveDataProcessed();
        order.verify(sessionDataListener).receiveDataAvailable();
        order.verify(application).onSessionMessage(
            same(session), eq(ContentType.BINARY), any(DirectBuffer.class), eq(0), eq(64));
        order.verify(sessionDataListener).receiveDataProcessed();
        order.verify(sessionDataListener).receiveDataAvailable();
        order.verify(application).onSessionMessage(
            same(session), eq(ContentType.BINARY), any(DirectBuffer.class), eq(0), eq(31));
        order.verify(sessionDataListener).receiveDataProcessed();
    }

    @Test
    void exceedingInternalBufferCapacityShouldCauseDataToBeQueuedUntilMaxSocketBufferSizeIsReached() throws IOException
    {
        final int payloadLength = INITIAL_SOCKET_BUFFER_CAPACITY - EncodingUtil.LARGE_MESSAGE_REQUEST_HEADER_LENGTH;
        given(application.onSessionMessage(any(), any(), any(), anyInt(), anyInt()))
            .willReturn(BACK_PRESSURE);
        provideSocketData(handshake(),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer));
        upgradeConnection();

        session.doReceiveWork();
        session.validated();
        session.doReceiveWork();
        session.doReceiveWork();
        session.doReceiveWork();
        session.doReceiveWork();
        session.doReceiveWork();

        assertThat(session.receiveBufferCapacity()).isEqualTo(MAX_SOCKET_BUFFER_SIZE);
    }

    @Test
    void shouldDrainSocketBufferAfterApplicationBackPressure() throws IOException
    {
        final int payloadLength = INITIAL_SOCKET_BUFFER_CAPACITY - EncodingUtil.LARGE_MESSAGE_REQUEST_HEADER_LENGTH;
        given(application.onSessionMessage(any(), any(), any(), anyInt(), anyInt()))
            .willReturn(BACK_PRESSURE, BACK_PRESSURE, BACK_PRESSURE, BACK_PRESSURE, OK);
        provideSocketData(handshake(),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer),
            buffer ->
            FrameUtil.writeWebSocketFrame(new byte[payloadLength], buffer));
        upgradeConnection();

        session.doReceiveWork();
        session.validated();
        session.doReceiveWork();
        session.doReceiveWork();
        session.doReceiveWork();
        session.doReceiveWork();
        session.doReceiveWork();
        session.doReceiveWork();
        session.doReceiveWork();
        session.doReceiveWork();

        assertThat(session.receiveBufferCapacity()).isEqualTo(INITIAL_SOCKET_BUFFER_CAPACITY * 4);
        verify(application, times(12))
            .onSessionMessage(same(session), eq(ContentType.BINARY), any(), anyInt(), eq(payloadLength));
    }

    @Test
    void shouldGrowReceiveBufferToHandleCompleteMessageLargerThanInitialBuffer() throws IOException
    {
        final int totalPayloadSize = (int)(INTERNAL_BUFFER_INITIAL_CAPACITY * 1.5);
        final int headerLength = EncodingUtil.requestHeaderLengthByPayloadLength(totalPayloadSize);
        final int firstReadSize = INTERNAL_BUFFER_INITIAL_CAPACITY - headerLength;
        final int remainingReadSize = totalPayloadSize - firstReadSize;
        provideSocketData(handshake(), buffer ->
        {
            FrameUtil.writeWebSocketFrame(new byte[totalPayloadSize], firstReadSize, buffer);
        }, buffer ->
            {
                buffer.put(new byte[remainingReadSize]);
            });
        upgradeConnection();

        given(application.onSessionMessage(any(), any(), any(), anyInt(), anyInt()))
            .willReturn(OK);

        session.doReceiveWork();
        session.validated();
        session.doReceiveWork();
        session.doReceiveWork();

        final InOrder order = Mockito.inOrder(sessionDataListener, connectionUpgrade, application);
        order.verify(sessionDataListener).receiveDataAvailable();
        order.verify(connectionUpgrade).handleUpgrade(any(), any());
        order.verify(sessionDataListener).receiveDataProcessed();
        order.verify(sessionDataListener).receiveDataAvailable();
        order.verify(application).onSessionMessage(
            same(session), eq(ContentType.BINARY), any(DirectBuffer.class), eq(0), eq(totalPayloadSize));
        order.verify(sessionDataListener).receiveDataProcessed();
    }

    private static Consumer<ByteBuffer> handshake()
    {
        return b ->
        {
            b.put((byte)7);
        };
    }

    @SuppressWarnings("unchecked")
    private void provideSocketData(final Consumer<ByteBuffer>... socketReads) throws IOException
    {
        doAnswer(new Answer<Integer>()
        {
            private int position = 0;

            @Override
            public Integer answer(final InvocationOnMock invocation)
            {
                final ByteBuffer buffer = invocation.getArgument(0);

                socketReads[position++].accept(buffer);
                return 42;
            }
        }).when(channel).read(any(ByteBuffer.class));
    }

    private void upgradeConnection()
    {
        doAnswer((Answer<Boolean>)invocation ->
        {
            final ByteBuffer receiveBuffer = invocation.getArgument(0);
            receiveBuffer.position(receiveBuffer.limit());
            return true;
        }).when(connectionUpgrade).handleUpgrade(any(), any());
    }
}
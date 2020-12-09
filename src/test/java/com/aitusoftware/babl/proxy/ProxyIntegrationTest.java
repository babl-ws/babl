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
package com.aitusoftware.babl.proxy;

import static com.aitusoftware.babl.codec.VarDataEncodingEncoder.varDataEncodingOffset;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.aitusoftware.babl.codec.MessageHeaderDecoder;
import com.aitusoftware.babl.codec.MessageHeaderEncoder;
import com.aitusoftware.babl.codec.SessionMessageDecoder;
import com.aitusoftware.babl.codec.SessionMessageEncoder;
import com.aitusoftware.babl.codec.SessionOpenedDecoder;
import com.aitusoftware.babl.codec.SessionOpenedEncoder;
import com.aitusoftware.babl.codec.VarDataEncodingDecoder;
import com.aitusoftware.babl.codec.VarDataEncodingEncoder;
import com.aitusoftware.babl.config.PerformanceMode;
import com.aitusoftware.babl.monitoring.NoOpApplicationAdapterStatistics;
import com.aitusoftware.babl.monitoring.NoOpSessionContainerAdapterStatistics;
import com.aitusoftware.babl.monitoring.NoOpSessionContainerStatistics;
import com.aitusoftware.babl.user.Application;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.websocket.broadcast.Broadcast;
import com.aitusoftware.babl.websocket.MaintainBackPressureStrategy;
import com.aitusoftware.babl.websocket.Session;

import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.SystemEpochClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatcher;

import io.aeron.Aeron;
import io.aeron.CommonContext;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;

class ProxyIntegrationTest
{
    private static final long SESSION_ID = 37L;
    private static final int SERVER_ID = 0;
    private static final int APPLICATION_CONTAINER_ID = 0;
    private static final int PAYLOAD_LENGTH = 100;
    private static final ContentType CONTENT_TYPE = ContentType.BINARY;
    private static final int MESSAGE_LENGTH = 100;
    private static final UnsafeBuffer MESSAGE = new UnsafeBuffer(new byte[MESSAGE_LENGTH]);
    private static final int POLL_FRAGMENT_LIMIT = 10;

    static
    {
        Arrays.fill(MESSAGE.byteArray(), (byte)11);
    }

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final SessionOpenedEncoder sessionOpenedEncoder = new SessionOpenedEncoder();
    private final SessionOpenedDecoder sessionOpenedDecoder = new SessionOpenedDecoder();
    private final SessionMessageEncoder sessionMessageEncoder = new SessionMessageEncoder();
    private final SessionMessageDecoder sessionMessageDecoder = new SessionMessageDecoder();
    private final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
    private final MutableDirectBuffer payload = new UnsafeBuffer(new byte[PAYLOAD_LENGTH]);
    private final Session session = mock(Session.class);
    private final Broadcast broadcast = mock(Broadcast.class);

    private static final byte PAYLOAD_VALUE = (byte)7;
    private final Application application = mock(Application.class);

    {
        Arrays.fill(payload.byteArray(), PAYLOAD_VALUE);
    }

    @TempDir
    Path tmpDir;
    private MediaDriver mediaDriver;
    private Aeron aeron;

    @BeforeEach
    void setUp()
    {
        final MediaDriver.Context driverContext = new MediaDriver.Context();
        driverContext.aeronDirectoryName(tmpDir.toString());
        ContextConfiguration.applySettings(PerformanceMode.LOW, driverContext);
        mediaDriver = MediaDriver.launch(driverContext);
        final Aeron.Context clientContext = new Aeron.Context();
        ContextConfiguration.applySettings(PerformanceMode.LOW, clientContext);
        clientContext.aeronDirectoryName(tmpDir.toString());
        aeron = Aeron.connect(clientContext);
        given(session.id()).willReturn(SESSION_ID);
    }

    @AfterEach
    void tearDown()
    {
        CloseHelper.closeAll(aeron, mediaDriver);
    }

    @Test
    void serverToApplication()
    {
        final Publication serverToApplicationPublication = aeron.addPublication(CommonContext.IPC_CHANNEL,
            APPLICATION_CONTAINER_ID);
        final Subscription serverToApplicationSubscription =
            aeron.addSubscription(CommonContext.IPC_CHANNEL, APPLICATION_CONTAINER_ID);
        final Publication applicationToServerPublication = aeron.addPublication(CommonContext.IPC_CHANNEL, SERVER_ID);
        final ApplicationAdapter applicationAdapter =
            new ApplicationAdapter(5, application, serverToApplicationSubscription,
            new Publication[] {applicationToServerPublication}, POLL_FRAGMENT_LIMIT,
            new NoOpApplicationAdapterStatistics(), 16, SystemEpochClock.INSTANCE);

        final ApplicationProxy applicationProxy = new ApplicationProxy(SERVER_ID, new Long2ObjectHashMap<>());
        applicationProxy.init(serverToApplicationPublication, new NoOpSessionContainerStatistics());
        applicationProxy.onSessionConnected(session);
        applicationProxy.onSessionMessage(session, CONTENT_TYPE, MESSAGE, 0, MESSAGE_LENGTH);
        while (0 != applicationAdapter.doWork() || serverToApplicationSubscription.images().get(0).position() == 0L)
        {
            idle();
        }
        verify(application).onSessionConnected(argThat(sessionWithId(SESSION_ID)));
        verify(application).onSessionMessage(argThat(sessionWithId(SESSION_ID)), eq(CONTENT_TYPE),
            notNull(DirectBuffer.class), anyInt(), eq(MESSAGE_LENGTH));
    }

    @Test
    void applicationToServer()
    {
        final Subscription applicationToServerSubscription =
            aeron.addSubscription(CommonContext.IPC_CHANNEL, SERVER_ID);
        final Publication applicationToServerPublication = aeron.addPublication(CommonContext.IPC_CHANNEL, SERVER_ID);
        final Long2ObjectHashMap<Session> sessionByIdMap = new Long2ObjectHashMap<>();
        sessionByIdMap.put(SESSION_ID, session);
        final SessionContainerAdapter sessionContainerAdapter = new SessionContainerAdapter(
            0, sessionByIdMap, applicationToServerSubscription, null, POLL_FRAGMENT_LIMIT,
            new MaintainBackPressureStrategy(), broadcast);
        sessionContainerAdapter.sessionAdapterStatistics(new NoOpSessionContainerAdapterStatistics());

        final SessionProxy sessionProxy = new SessionProxy(
            new Publication[] {applicationToServerPublication}, new NoOpApplicationAdapterStatistics());
        sessionProxy.set(SESSION_ID, SERVER_ID);

        sessionProxy.send(CONTENT_TYPE, MESSAGE, 0, MESSAGE_LENGTH);
        final long deadlineMs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(20);
        while (0 != sessionContainerAdapter.doWork() ||
            applicationToServerSubscription.images().isEmpty() ||
            applicationToServerSubscription.images().get(0).position() == 0L)
        {
            idle();
            if (System.currentTimeMillis() > deadlineMs)
            {
                Assertions.fail("Failed to find image within timeout");
            }
        }

        verify(session).send(eq(CONTENT_TYPE), any(), anyInt(), eq(MESSAGE_LENGTH));
    }

    @Test
    void shouldWrapMessages()
    {
        sessionOpenedEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
        sessionOpenedEncoder.sessionId(SESSION_ID);
        sessionOpenedEncoder.containerId(SERVER_ID);

        headerDecoder.wrap(buffer, 0);
        sessionOpenedDecoder.wrap(buffer, headerDecoder.encodedLength(),
            headerDecoder.blockLength(), headerDecoder.version());

        assertThat(headerDecoder.templateId()).isEqualTo(SessionOpenedDecoder.TEMPLATE_ID);
        assertThat(sessionOpenedDecoder.sessionId()).isEqualTo(SESSION_ID);
        assertThat(sessionOpenedDecoder.containerId()).isEqualTo(SERVER_ID);

        sessionMessageEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder);
        sessionMessageEncoder.sessionId(SESSION_ID);
        sessionMessageEncoder.containerId(SERVER_ID);
        final VarDataEncodingEncoder encodedMessage = sessionMessageEncoder.message();
        encodedMessage.length(PAYLOAD_LENGTH);
        encodedMessage.buffer().putBytes(
            encodedMessage.offset() + varDataEncodingOffset(), payload, 0, PAYLOAD_LENGTH);

        headerDecoder.wrap(buffer, 0);
        sessionMessageDecoder.wrap(buffer, headerDecoder.encodedLength(),
            headerDecoder.blockLength(), headerDecoder.version());
        assertThat(headerDecoder.templateId()).isEqualTo(SessionMessageDecoder.TEMPLATE_ID);
        assertThat(sessionMessageDecoder.sessionId()).isEqualTo(SESSION_ID);
        assertThat(sessionMessageDecoder.containerId()).isEqualTo(SERVER_ID);
        final VarDataEncodingDecoder decodedMessage = sessionMessageDecoder.message();
        assertThat(decodedMessage.length()).isEqualTo(PAYLOAD_LENGTH);
        final byte[] tmp = new byte[PAYLOAD_LENGTH];
        final Byte[] expected = new Byte[PAYLOAD_LENGTH];
        Arrays.fill(expected, PAYLOAD_VALUE);
        decodedMessage.buffer().getBytes(decodedMessage.offset() + varDataEncodingOffset(), tmp);
        assertThat(tmp).asList().containsExactlyElementsIn(expected);
    }

    private static void idle()
    {
        LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(1L));
    }

    private static ArgumentMatcher<Session> sessionWithId(final long expectedId)
    {
        return new SessionMatcher(expectedId);
    }

    private static class SessionMatcher implements ArgumentMatcher<Session>
    {
        private final long expectedId;

        SessionMatcher(final long expectedId)
        {
            this.expectedId = expectedId;
        }

        @Override
        public boolean matches(final Session argument)
        {
            return argument.id() == expectedId;
        }
    }
}
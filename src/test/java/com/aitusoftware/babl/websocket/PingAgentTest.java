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
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.concurrent.ThreadLocalRandom;

import com.aitusoftware.babl.monitoring.NoOpSessionContainerStatistics;
import com.aitusoftware.babl.monitoring.NoOpSessionStatistics;
import com.aitusoftware.babl.pool.BufferPool;
import com.aitusoftware.babl.config.SessionConfig;

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PingAgentTest
{
    private static final long BASE_TIME = System.currentTimeMillis();
    private static final long SESSION_ID = 17L;
    private static final NoOpSessionDataListener SESSION_DATA_LISTENER = new NoOpSessionDataListener();
    private static final long PONG_RESPONSE_TIMEOUT_MS = 30_000L;
    private static final long PING_INTERVAL_MS = 10_000L;
    private final FrameEncoder frameEncoder = new FrameEncoder(new BufferPool(),
        SESSION_DATA_LISTENER, new SessionConfig(), new NoOpSessionContainerStatistics());
    private final EpochClock clock = mock(EpochClock.class);
    private final PingAgent.PingPayloadSupplier pingPayloadSupplier = mock(PingAgent.PingPayloadSupplier.class);
    private final PingAgent pingAgent = new PingAgent(frameEncoder, clock,
        PING_INTERVAL_MS, PONG_RESPONSE_TIMEOUT_MS, SESSION_DATA_LISTENER, pingPayloadSupplier);
    private final MutableDirectBuffer payloadBuffer = new UnsafeBuffer(new byte[BitUtil.SIZE_OF_LONG]);

    @BeforeEach
    void setUp()
    {
        frameEncoder.init(new NoOpSessionStatistics(), 0L);
        given(clock.time()).willReturn(BASE_TIME);
        pingAgent.init(SESSION_ID);
    }

    @Test
    void shouldWritePingAfterTimeout()
    {
        given(clock.time()).willReturn(BASE_TIME + 1, BASE_TIME + PING_INTERVAL_MS + 1);
        pingAgent.doWork();
        assertThat(frameEncoder.sendBuffer().remaining()).isEqualTo(frameEncoder.sendBuffer().capacity());
        pingAgent.doWork();

        assertWebSocketFrame(frameEncoder.sendBuffer(), 0, BitUtil.SIZE_OF_LONG, Constants.OPCODE_PING, true);
    }

    @Test
    void shouldWritePongAfterPingReceived()
    {
        payloadBuffer.putLong(0, ThreadLocalRandom.current().nextLong());
        pingAgent.pingReceived(payloadBuffer, 0, BitUtil.SIZE_OF_LONG);
        assertThat(frameEncoder.sendBuffer().remaining()).isEqualTo(frameEncoder.sendBuffer().capacity());
        pingAgent.doWork();

        assertWebSocketFrame(frameEncoder.sendBuffer(), 0, BitUtil.SIZE_OF_LONG, Constants.OPCODE_PONG, true);
    }

    @Test
    void connectionShouldBeAliveAfterReceivingValidPongPayload()
    {
        assertPongResultsInConnectionState(0xFEED1355L, 0xFEED1355L, true);
    }

    @Test
    void connectionShouldNotBeAliveAfterReceivingInvalidPongPayload()
    {
        assertPongResultsInConnectionState(0xFEED1355L, 0xCAFECAFEL, false);
    }

    private void assertPongResultsInConnectionState(
        final long pingPayload,
        final long pongPayload,
        final boolean expectedToBeConnected)
    {
        given(pingPayloadSupplier.supplyPingPayload()).willReturn(pingPayload);
        final long pingSendTime = BASE_TIME + PING_INTERVAL_MS + 1;
        final long afterPongTimeout = pingSendTime + PONG_RESPONSE_TIMEOUT_MS + 1;
        given(clock.time()).willReturn(pingSendTime, afterPongTimeout);
        pingAgent.doWork();
        payloadBuffer.putLong(0, pongPayload);
        pingAgent.pongReceived(payloadBuffer, 0, BitUtil.SIZE_OF_LONG);

        assertThat(pingAgent.connectionIsAlive()).isEqualTo(expectedToBeConnected);
    }
}
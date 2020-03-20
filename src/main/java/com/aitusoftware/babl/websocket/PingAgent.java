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


import com.aitusoftware.babl.log.Category;
import com.aitusoftware.babl.log.Logger;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.UnsafeBuffer;

final class PingAgent
{
    static final int MAX_PING_PAYLOAD_LENGTH = 125;

    private final FrameEncoder frameEncoder;
    private final EpochClock clock;
    private final long pingIntervalMs;
    private final long pongResponseTimeoutMs;
    private final MutableDirectBuffer pingPayloadBuffer = new UnsafeBuffer(new byte[BitUtil.SIZE_OF_LONG]);
    private final MutableDirectBuffer pongPayloadBuffer = new UnsafeBuffer(new byte[MAX_PING_PAYLOAD_LENGTH]);
    private final SessionDataListener sessionDataListener;
    private final PingPayloadSupplier pingPayloadSupplier;
    private long nextPingTimestampMs;
    private long lastPingPayload;
    private long pongResponseDeadlineMs = Long.MAX_VALUE;
    private boolean awaitingPong = false;
    private int pongPayloadLength;
    private long sessionId;

    PingAgent(
        final FrameEncoder frameEncoder,
        final EpochClock clock,
        final long pingIntervalMs,
        final long pongResponseTimeoutMs,
        final SessionDataListener sessionDataListener)
    {
        this(frameEncoder, clock, pingIntervalMs, pongResponseTimeoutMs,
            sessionDataListener, new NanoTimePingPayloadSupplier());
    }

    PingAgent(
        final FrameEncoder frameEncoder,
        final EpochClock clock,
        final long pingIntervalMs,
        final long pongResponseTimeoutMs,
        final SessionDataListener sessionDataListener,
        final PingPayloadSupplier pingPayloadSupplier)
    {
        this.frameEncoder = frameEncoder;
        this.clock = clock;
        this.pingIntervalMs = pingIntervalMs;
        this.pongResponseTimeoutMs = pongResponseTimeoutMs;
        this.sessionDataListener = sessionDataListener;
        this.pingPayloadSupplier = pingPayloadSupplier;
    }

    void reset()
    {
        sessionId = -1;
        awaitingPong = false;
        nextPingTimestampMs = 0;
        pongResponseDeadlineMs = 0;
    }

    void init(final long sessionId)
    {
        this.sessionId = sessionId;
        nextPingTimestampMs = clock.time() + pingIntervalMs;
    }

    int doWork()
    {
        int workDone = 0;
        final long time = clock.time();
        if (pingRequired(time) && tryWritePing())
        {
            nextPingTimestampMs = time + pingIntervalMs;
            pongResponseDeadlineMs = time + pongResponseTimeoutMs;
            awaitingPong = true;
            sessionDataListener.sendDataAvailable();
            workDone = 1;
        }
        if (pongRequired() && tryWritePong())
        {
            pongPayloadLength = 0;
            sessionDataListener.sendDataAvailable();
            workDone = 1;
        }

        return workDone;
    }

    void messageReceived()
    {
        nextPingTimestampMs = clock.time() + pingIntervalMs;
    }

    boolean connectionIsAlive()
    {
        return !(awaitingPong && clock.time() > pongResponseDeadlineMs);
    }

    void pongReceived(final DirectBuffer buffer, final int offset, final int length)
    {
        if (length >= BitUtil.SIZE_OF_LONG && buffer.getLong(offset) == lastPingPayload)
        {
            awaitingPong = false;
            pongResponseDeadlineMs = Long.MAX_VALUE;
        }
    }

    void pingReceived(final DirectBuffer payloadBuffer, final int offset, final int length)
    {
        pongPayloadBuffer.putBytes(0, payloadBuffer, offset, length);
        pongPayloadLength = length;
    }

    private boolean pingRequired(final long time)
    {
        return time > nextPingTimestampMs;
    }

    private boolean tryWritePing()
    {
        final long pingPayload = pingPayloadSupplier.supplyPingPayload();
        pingPayloadBuffer.putLong(0, pingPayload);
        final boolean pingSent = frameEncoder.encodePing(pingPayloadBuffer, 0, BitUtil.SIZE_OF_LONG) == SendResult.OK;
        if (pingSent)
        {
            this.lastPingPayload = pingPayload;
            Logger.log(Category.HEARTBEAT, "Sent ping on session %d%n", sessionId);
        }
        return pingSent;
    }

    private boolean tryWritePong()
    {
        final boolean pongSent = frameEncoder.encodePong(pongPayloadBuffer, 0, pongPayloadLength) == SendResult.OK;
        if (pongSent)
        {
            Logger.log(Category.HEARTBEAT, "Sent pong on session %d%n", sessionId);
        }
        return pongSent;
    }

    boolean pongRequired()
    {
        return pongPayloadLength != 0;
    }

    abstract static class PingPayloadSupplier
    {
        abstract long supplyPingPayload();
    }

    private static final class NanoTimePingPayloadSupplier extends PingPayloadSupplier
    {
        @Override
        long supplyPingPayload()
        {
            return System.nanoTime();
        }
    }
}
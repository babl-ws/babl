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
import static com.aitusoftware.babl.proxy.ProxyUtil.acquireBuffer;

import java.util.function.Supplier;

import com.aitusoftware.babl.codec.ApplicationMessageEncoder;
import com.aitusoftware.babl.codec.CloseSessionEncoder;
import com.aitusoftware.babl.codec.MessageHeaderEncoder;
import com.aitusoftware.babl.codec.VarDataEncodingEncoder;
import com.aitusoftware.babl.log.Category;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.monitoring.ApplicationAdapterStatistics;
import com.aitusoftware.babl.monitoring.BackPressureStatus;
import com.aitusoftware.babl.pool.Pooled;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.websocket.DisconnectReason;
import com.aitusoftware.babl.websocket.SendResult;
import com.aitusoftware.babl.websocket.Session;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;

import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;

final class SessionProxy implements Session, Pooled
{
    private static final int HEADER_LENGTH = MessageHeaderEncoder.ENCODED_LENGTH;
    private static final int APPLICATION_MESSAGE_BASE_SIZE =
        HEADER_LENGTH + ApplicationMessageEncoder.BLOCK_LENGTH + BitUtil.SIZE_OF_INT;
    private static final int CLOSE_SESSION_SIZE = HEADER_LENGTH + CloseSessionEncoder.BLOCK_LENGTH;

    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final ApplicationMessageEncoder applicationMessageEncoder = new ApplicationMessageEncoder();
    private final CloseSessionEncoder closeSessionEncoder = new CloseSessionEncoder();
    private final BufferClaim bufferClaim = new BufferClaim();
    private final Publication[] toServerPublications;
    private final ApplicationAdapterStatistics applicationAdapterStatistics;
    private long sessionId;
    private int sessionContainerId;
    private Publication currentServerPublication;

    SessionProxy(
        final Publication[] publications,
        final ApplicationAdapterStatistics applicationAdapterStatistics)
    {
        this.toServerPublications = publications;
        this.applicationAdapterStatistics = applicationAdapterStatistics;
    }

    void set(final long sessionId, final int sessionContainerId)
    {
        this.sessionId = sessionId;
        this.sessionContainerId = sessionContainerId;
        currentServerPublication = toServerPublications[sessionContainerId];
    }

    @Override
    public int send(
        final ContentType contentType,
        final DirectBuffer buffer,
        final int offset,
        final int length)
    {
        final long result = acquireBuffer(
            length + APPLICATION_MESSAGE_BASE_SIZE, currentServerPublication, bufferClaim);
        if (result > 0)
        {
            applicationMessageEncoder.wrapAndApplyHeader(
                bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder);
            applicationMessageEncoder.sessionId(sessionId);
            applicationMessageEncoder.containerId(sessionContainerId);
            applicationMessageEncoder.contentType(contentType.ordinal());

            final VarDataEncodingEncoder encodedMessage = applicationMessageEncoder.message();
            encodedMessage.length(length);
            encodedMessage.buffer().putBytes(
                encodedMessage.offset() + varDataEncodingOffset(), buffer, offset, length);
            bufferClaim.commit();
            Logger.log(Category.PROXY, "[%d] SessionProxy send(sessionId: %d)%n",
                sessionContainerId, sessionId);
            applicationAdapterStatistics.proxyBackPressured(BackPressureStatus.NOT_BACK_PRESSURED);
            return SendResult.OK;
        }
        bufferClaim.abort();
        if (result == Publication.BACK_PRESSURED)
        {
            applicationAdapterStatistics.proxyBackPressure();
            applicationAdapterStatistics.proxyBackPressured(BackPressureStatus.BACK_PRESSURED);
        }
        return ProxyUtil.offerResultToSendResult(result);
    }

    @Override
    public int close(final DisconnectReason disconnectReason)
    {
        final long result = acquireBuffer(
            CLOSE_SESSION_SIZE, currentServerPublication, bufferClaim);
        if (result > 0)
        {
            closeSessionEncoder.wrapAndApplyHeader(
                bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder);
            closeSessionEncoder.sessionId(sessionId);
            closeSessionEncoder.containerId(sessionContainerId);
            closeSessionEncoder.closeReason(disconnectReason.ordinal());
            bufferClaim.commit();
            Logger.log(Category.PROXY, "[%d] SessionProxy close(sessionId: %d)%n", sessionContainerId, sessionId);
            return SendResult.OK;
        }
        bufferClaim.abort();
        return ProxyUtil.offerResultToSendResult(result);
    }

    @Override
    public long id()
    {
        return sessionId;
    }

    @Override
    public void reset()
    {
        this.sessionId = Long.MIN_VALUE;
        this.sessionContainerId = Integer.MIN_VALUE;
    }

    @Override
    public String toString()
    {
        return String.format("SessionProxy{sessionId: %d, sessionContainerId: %d}",
            sessionId, sessionContainerId);
    }

    static final class Factory implements Supplier<SessionProxy>
    {
        private final Publication[] toServerPublications;
        private final ApplicationAdapterStatistics applicationAdapterStatistics;

        Factory(
            final Publication[] toServerPublications,
            final ApplicationAdapterStatistics applicationAdapterStatistics)
        {
            this.toServerPublications = toServerPublications;
            this.applicationAdapterStatistics = applicationAdapterStatistics;
        }

        @Override
        public SessionProxy get()
        {
            return new SessionProxy(toServerPublications, applicationAdapterStatistics);
        }
    }
}
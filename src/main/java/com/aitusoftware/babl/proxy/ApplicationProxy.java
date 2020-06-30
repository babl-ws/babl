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

import com.aitusoftware.babl.codec.MessageHeaderEncoder;
import com.aitusoftware.babl.codec.SessionClosedEncoder;
import com.aitusoftware.babl.codec.SessionMessageEncoder;
import com.aitusoftware.babl.codec.SessionOpenedEncoder;
import com.aitusoftware.babl.codec.VarDataEncodingEncoder;
import com.aitusoftware.babl.log.Category;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.monitoring.BackPressureStatus;
import com.aitusoftware.babl.monitoring.SessionContainerStatistics;
import com.aitusoftware.babl.user.Application;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.websocket.DisconnectReason;
import com.aitusoftware.babl.websocket.SendResult;
import com.aitusoftware.babl.websocket.Session;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;

/**
 * IPC proxy to send messages to an application.
 */
public final class ApplicationProxy implements Application
{
    private static final int HEADER_LENGTH = MessageHeaderEncoder.ENCODED_LENGTH;
    private static final int SESSION_OPENED_SIZE = HEADER_LENGTH + SessionOpenedEncoder.BLOCK_LENGTH;
    private static final int SESSION_CLOSED_SIZE = HEADER_LENGTH + SessionClosedEncoder.BLOCK_LENGTH;
    private static final int SESSION_MESSAGE_BASE_SIZE =
        HEADER_LENGTH + SessionMessageEncoder.BLOCK_LENGTH + BitUtil.SIZE_OF_INT;
    private final Long2ObjectHashMap<Session> sessionByIdMap;
    private final BufferClaim bufferClaim = new BufferClaim();
    private final SessionOpenedEncoder sessionOpenEncoder = new SessionOpenedEncoder();
    private final SessionClosedEncoder sessionCloseEncoder = new SessionClosedEncoder();
    private final SessionMessageEncoder sessionMessageEncoder = new SessionMessageEncoder();
    private final int sessionContainerId;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    private Publication applicationsPublication;
    private SessionContainerStatistics sessionContainerStatistics;

    /**
     * Constructor.
     *
     * @param sessionContainerId identifier for the session container instance using this proxy
     * @param sessionByIdMap     map containing sessions keyed by ID
     */
    public ApplicationProxy(
        final int sessionContainerId,
        final Long2ObjectHashMap<Session> sessionByIdMap)
    {
        this.sessionByIdMap = sessionByIdMap;
        this.sessionContainerId = sessionContainerId;
    }

    /**
     * Initialises the proxy with a publication.
     *
     * @param applicationsPublication  publication to the application
     * @param sessionContainerStatistics sink for metrics
     */
    public void init(
        final Publication applicationsPublication,
        final SessionContainerStatistics sessionContainerStatistics)
    {
        this.applicationsPublication = applicationsPublication;
        this.sessionContainerStatistics = sessionContainerStatistics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int onSessionConnected(final Session session)
    {
        sessionByIdMap.put(session.id(), session);
        final long result = acquireBuffer(SESSION_OPENED_SIZE, applicationsPublication, bufferClaim);
        if (result >= 0)
        {
            sessionOpenEncoder.wrapAndApplyHeader(bufferClaim.buffer(), bufferClaim.offset(), headerEncoder);
            sessionOpenEncoder.sessionId(session.id());
            sessionOpenEncoder.containerId(sessionContainerId);
            bufferClaim.commit();
            Logger.log(Category.PROXY, "[%d] ApplicationProxy onSessionConnected(sessionId: %d)%n",
                sessionContainerId, session.id());
            proxyNotBackPressured();
            return SendResult.OK;
        }
        proxyBackPressured();
        return SendResult.BACK_PRESSURE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int onSessionDisconnected(final Session session, final DisconnectReason reason)
    {
        final long result = acquireBuffer(SESSION_CLOSED_SIZE, applicationsPublication, bufferClaim);
        if (result >= 0)
        {
            sessionCloseEncoder.wrapAndApplyHeader(bufferClaim.buffer(), bufferClaim.offset(), headerEncoder);
            sessionCloseEncoder.sessionId(session.id());
            sessionCloseEncoder.containerId(sessionContainerId);
            sessionCloseEncoder.closeReason(reason.ordinal());
            bufferClaim.commit();
            sessionByIdMap.remove(session.id());
            Logger.log(Category.PROXY, "[%d] ApplicationProxy onSessionDisconnected(sessionId: %d)%n",
                sessionContainerId, session.id());
            proxyNotBackPressured();
            return SendResult.OK;
        }
        proxyBackPressured();
        return SendResult.BACK_PRESSURE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int onSessionMessage(
        final Session session,
        final ContentType contentType,
        final DirectBuffer msg,
        final int offset,
        final int length)
    {
        final long result = acquireBuffer(
            SESSION_MESSAGE_BASE_SIZE + length, applicationsPublication, bufferClaim);
        if (result > 0)
        {
            sessionMessageEncoder.wrapAndApplyHeader(bufferClaim.buffer(), bufferClaim.offset(), headerEncoder);
            sessionMessageEncoder.sessionId(session.id());
            sessionMessageEncoder.containerId(sessionContainerId);
            sessionMessageEncoder.contentType(contentType.ordinal());

            final VarDataEncodingEncoder encodedMessage = sessionMessageEncoder.message();
            encodedMessage.length(length);
            encodedMessage.buffer().putBytes(
                encodedMessage.offset() + varDataEncodingOffset(), msg, offset, length);
            bufferClaim.commit();
            Logger.log(Category.PROXY, "[%d] ApplicationProxy onSessionMessage(sessionId: %d)%n",
                sessionContainerId, session.id());
            proxyNotBackPressured();
            return SendResult.OK;
        }
        bufferClaim.abort();
        if (result == Publication.BACK_PRESSURED)
        {
            proxyBackPressured();
        }
        return ProxyUtil.offerResultToSendResult(result);
    }

    private void proxyNotBackPressured()
    {
        sessionContainerStatistics.proxyBackPressured(BackPressureStatus.NOT_BACK_PRESSURED);
    }

    private void proxyBackPressured()
    {
        sessionContainerStatistics.onProxyBackPressure();
        sessionContainerStatistics.proxyBackPressured(BackPressureStatus.BACK_PRESSURED);
    }
}
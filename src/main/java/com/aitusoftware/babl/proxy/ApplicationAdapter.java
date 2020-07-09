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
import static com.aitusoftware.babl.proxy.ProxyUtil.sendResultToAction;

import com.aitusoftware.babl.codec.MessageHeaderDecoder;
import com.aitusoftware.babl.codec.SessionClosedDecoder;
import com.aitusoftware.babl.codec.SessionMessageDecoder;
import com.aitusoftware.babl.codec.SessionOpenedDecoder;
import com.aitusoftware.babl.codec.VarDataEncodingDecoder;
import com.aitusoftware.babl.log.Category;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.monitoring.ApplicationAdapterStatistics;
import com.aitusoftware.babl.monitoring.EventLoopDurationReporter;
import com.aitusoftware.babl.user.Application;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.websocket.DisconnectReason;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.EpochClock;

import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.ControlledFragmentHandler;
import io.aeron.logbuffer.Header;

/**
 * IPC consumer to process messages coming from the session container.
 */
public final class ApplicationAdapter implements Agent, ControlledFragmentHandler
{
    private static final ContentType[] CONTENT_TYPES = ContentType.values();
    private static final DisconnectReason[] DISCONNECT_REASONS = DisconnectReason.values();
    private final SessionOpenedDecoder sessionOpenDecoder = new SessionOpenedDecoder();
    private final SessionClosedDecoder sessionCloseDecoder = new SessionClosedDecoder();
    private final SessionMessageDecoder sessionMessageDecoder = new SessionMessageDecoder();
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final String agentName;
    private final Application application;
    private final Subscription fromServerSubscription;
    private final EpochClock clock;
    private final SessionProxy sessionProxy;
    private final int applicationAdapterPollFragmentLimit;
    private final ApplicationAdapterStatistics applicationAdapterStatistics;
    private final EventLoopDurationReporter eventLoopDurationReporter;

    /**
     * Constructor.
     *
     * @param instanceId                          application instance ID
     * @param application                         user application
     * @param fromServerSubscription              IPC subscription for messages from session containers
     * @param toServerPublications                IPC publications back to session containers
     * @param applicationAdapterPollFragmentLimit maximum number of messages to process per invocation of the event-loop
     * @param applicationAdapterStatistics        sink for metrics
     * @param clock                               clock for timing events
     */
    public ApplicationAdapter(
        final int instanceId,
        final Application application,
        final Subscription fromServerSubscription,
        final Publication[] toServerPublications,
        final int applicationAdapterPollFragmentLimit,
        final ApplicationAdapterStatistics applicationAdapterStatistics,
        final EpochClock clock)
    {
        agentName = "babl-application-container-" + instanceId;
        this.application = application;
        this.fromServerSubscription = fromServerSubscription;
        this.clock = clock;
        sessionProxy = new SessionProxy(toServerPublications, applicationAdapterStatistics);
        this.applicationAdapterPollFragmentLimit = applicationAdapterPollFragmentLimit;
        this.applicationAdapterStatistics = applicationAdapterStatistics;
        this.eventLoopDurationReporter = new EventLoopDurationReporter(
            applicationAdapterStatistics::eventLoopDurationMs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int doWork()
    {
        eventLoopDurationReporter.eventLoopStart(clock.time());
        final int workDone = fromServerSubscription.controlledPoll(this, applicationAdapterPollFragmentLimit);
        if (workDone == applicationAdapterPollFragmentLimit)
        {
            applicationAdapterStatistics.adapterPollLimitReached();
        }
        final long timeMs = clock.time();
        applicationAdapterStatistics.heartbeat(timeMs);
        eventLoopDurationReporter.eventLoopComplete(timeMs);
        return workDone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Action onFragment(
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header)
    {
        Action action = Action.CONTINUE;
        final int applicationResult;
        messageHeaderDecoder.wrap(buffer, offset);
        switch (messageHeaderDecoder.templateId())
        {
            case SessionMessageDecoder.TEMPLATE_ID:
                sessionMessageDecoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH,
                    messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());
                setSessionProxy(sessionMessageDecoder.sessionId(), sessionMessageDecoder.containerId());
                final ContentType contentType = CONTENT_TYPES[sessionMessageDecoder.contentType()];
                final VarDataEncodingDecoder decodedMessage = sessionMessageDecoder.message();
                Logger.log(Category.PROXY, "[%d] ApplicationAdapter onSessionMessage(sessionId: %d)%n",
                    sessionMessageDecoder.containerId(), sessionMessageDecoder.sessionId());
                applicationResult = application.onSessionMessage(
                    sessionProxy, contentType, decodedMessage.buffer(),
                    decodedMessage.offset() + varDataEncodingOffset(), (int)decodedMessage.length());
                action = sendResultToAction(applicationResult);
                break;
            case SessionOpenedDecoder.TEMPLATE_ID:
                sessionOpenDecoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH,
                    messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());
                setSessionProxy(sessionOpenDecoder.sessionId(), sessionOpenDecoder.containerId());
                Logger.log(Category.PROXY, "[%d] ApplicationAdapter onSessionConnected(sessionId: %d)%n",
                    sessionOpenDecoder.containerId(), sessionOpenDecoder.sessionId());
                applicationResult = application.onSessionConnected(sessionProxy);
                action = sendResultToAction(applicationResult);
                break;
            case SessionClosedDecoder.TEMPLATE_ID:
                sessionCloseDecoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH,
                    messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());
                setSessionProxy(sessionCloseDecoder.sessionId(), sessionCloseDecoder.containerId());
                Logger.log(Category.PROXY, "[%d] ApplicationAdapter onSessionDisconnected(sessionId: %d)%n",
                    sessionCloseDecoder.containerId(), sessionCloseDecoder.sessionId());
                applicationResult = application.onSessionDisconnected(sessionProxy,
                    DISCONNECT_REASONS[sessionCloseDecoder.closeReason()]);
                action = sendResultToAction(applicationResult);
                break;
            default:
                // ignore unknown message type
        }

        return action;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String roleName()
    {
        return agentName;
    }

    private void setSessionProxy(final long sessionId, final int sessionContainerId)
    {
        sessionProxy.set(sessionId, sessionContainerId);
    }
}
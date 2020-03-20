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

import com.aitusoftware.babl.codec.ApplicationMessageDecoder;
import com.aitusoftware.babl.codec.CloseSessionDecoder;
import com.aitusoftware.babl.codec.MessageHeaderDecoder;
import com.aitusoftware.babl.codec.VarDataEncodingDecoder;
import com.aitusoftware.babl.monitoring.SessionAdapterStatistics;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.websocket.BackPressureStrategy;
import com.aitusoftware.babl.websocket.DisconnectReason;
import com.aitusoftware.babl.websocket.SendResult;
import com.aitusoftware.babl.websocket.Session;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.Agent;

import io.aeron.Subscription;
import io.aeron.logbuffer.ControlledFragmentHandler;
import io.aeron.logbuffer.Header;

/**
 * IPC consumer to process messages coming from the application.
 */
public final class SessionContainerAdapter implements ControlledFragmentHandler, Agent
{
    private static final ContentType[] CONTENT_TYPES = ContentType.values();
    private static final DisconnectReason[] DISCONNECT_REASONS = DisconnectReason.values();
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final ApplicationMessageDecoder applicationMessageDecoder = new ApplicationMessageDecoder();
    private final CloseSessionDecoder closeSessionDecoder = new CloseSessionDecoder();
    private final int sessionContainerId;
    private final Long2ObjectHashMap<Session> sessionByIdMap;
    private final Subscription fromApplicationSubscription;
    private final int sessionAdapterPollFragmentLimit;
    private final BackPressureStrategy backPressureStrategy;
    private final String agentName;
    private SessionAdapterStatistics sessionAdapterStatistics;

    /**
     * Constructor.
     *
     * @param sessionContainerId              identifier for the session container instance
     * @param sessionByIdMap                  map containing sessions keyed by ID
     * @param fromApplicationSubscription     IPC subscription for messages from the application
     * @param sessionAdapterPollFragmentLimit maximum number of messages to process per invocation of the event-loop
     * @param backPressureStrategy            strategy to deal with back-pressure when sending to remote peers
     */
    public SessionContainerAdapter(
        final int sessionContainerId,
        final Long2ObjectHashMap<Session> sessionByIdMap,
        final Subscription fromApplicationSubscription,
        final int sessionAdapterPollFragmentLimit,
        final BackPressureStrategy backPressureStrategy)
    {
        this.sessionContainerId = sessionContainerId;
        this.sessionByIdMap = sessionByIdMap;
        this.fromApplicationSubscription = fromApplicationSubscription;
        this.sessionAdapterPollFragmentLimit = sessionAdapterPollFragmentLimit;
        this.backPressureStrategy = backPressureStrategy;
        agentName = "babl-session-adapter-" + sessionContainerId;
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
        messageHeaderDecoder.wrap(buffer, offset);
        if (messageHeaderDecoder.templateId() == ApplicationMessageDecoder.TEMPLATE_ID)
        {
            applicationMessageDecoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH,
                messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());
            if (applicationMessageDecoder.containerId() != sessionContainerId)
            {
                return action;
            }
            final long sessionId = applicationMessageDecoder.sessionId();
            final ContentType contentType = CONTENT_TYPES[applicationMessageDecoder.contentType()];
            final VarDataEncodingDecoder decodedMessage = applicationMessageDecoder.message();

            final Session session = sessionByIdMap.get(sessionId);
            int sendResult = session.send(contentType, decodedMessage.buffer(),
                decodedMessage.offset() + varDataEncodingOffset(), (int)decodedMessage.length());
            if (sendResult == SendResult.BACK_PRESSURE)
            {
                sendResult = backPressureStrategy.onSessionBackPressure(session);
            }
            action = sendResultToAction(sendResult);
        }
        else if (messageHeaderDecoder.templateId() == CloseSessionDecoder.TEMPLATE_ID)
        {
            closeSessionDecoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH,
                messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());
            if (applicationMessageDecoder.containerId() != sessionContainerId)
            {
                return action;
            }
            final long sessionId = closeSessionDecoder.sessionId();
            final DisconnectReason disconnectReason = DISCONNECT_REASONS[closeSessionDecoder.closeReason()];
            final Session session = sessionByIdMap.get(sessionId);
            session.close(disconnectReason);
        }

        return action;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int doWork()
    {
        final int workDone = fromApplicationSubscription.controlledPoll(this, sessionAdapterPollFragmentLimit);
        if (workDone == sessionAdapterPollFragmentLimit)
        {
            sessionAdapterStatistics.adapterPollLimitReached();
        }
        return workDone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String roleName()
    {
        return agentName;
    }

    /**
     * Sets the metrics sink.
     *
     * @param statistics metrics sink
     */
    public void sessionAdapterStatistics(final SessionAdapterStatistics statistics)
    {
        this.sessionAdapterStatistics = statistics;
    }
}
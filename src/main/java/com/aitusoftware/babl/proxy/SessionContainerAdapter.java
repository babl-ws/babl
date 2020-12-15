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

import com.aitusoftware.babl.codec.AddSessionToTopicDecoder;
import com.aitusoftware.babl.codec.ApplicationMessageDecoder;
import com.aitusoftware.babl.codec.CloseSessionDecoder;
import com.aitusoftware.babl.codec.CreateTopicDecoder;
import com.aitusoftware.babl.codec.DeleteTopicDecoder;
import com.aitusoftware.babl.codec.MessageHeaderDecoder;
import com.aitusoftware.babl.codec.MultiTopicMessageDecoder;
import com.aitusoftware.babl.codec.RemoveSessionFromTopicDecoder;
import com.aitusoftware.babl.codec.TopicMessageDecoder;
import com.aitusoftware.babl.codec.VarDataEncodingDecoder;
import com.aitusoftware.babl.log.Category;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.monitoring.SessionContainerAdapterStatistics;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.websocket.BackPressureStrategy;
import com.aitusoftware.babl.websocket.broadcast.Broadcast;
import com.aitusoftware.babl.websocket.DisconnectReason;
import com.aitusoftware.babl.websocket.SendResult;
import com.aitusoftware.babl.websocket.Session;

import org.agrona.BitUtil;
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
    private final CreateTopicDecoder createTopicDecoder = new CreateTopicDecoder();
    private final DeleteTopicDecoder deleteTopicDecoder = new DeleteTopicDecoder();
    private final AddSessionToTopicDecoder addToTopicDecoder = new AddSessionToTopicDecoder();
    private final RemoveSessionFromTopicDecoder removeFromTopicDecoder = new RemoveSessionFromTopicDecoder();
    private final TopicMessageDecoder topicMessageDecoder = new TopicMessageDecoder();
    private final MultiTopicMessageDecoder multiTopicMessageDecoder = new MultiTopicMessageDecoder();
    private final int sessionContainerId;
    private final Long2ObjectHashMap<Session> sessionByIdMap;
    private final Subscription fromApplicationSubscription;
    private final Subscription broadcastSubscription;
    private final int sessionAdapterPollFragmentLimit;
    private final BackPressureStrategy backPressureStrategy;
    private final String agentName;
    private final Broadcast broadcast;
    private SessionContainerAdapterStatistics sessionContainerAdapterStatistics;
    private int[] topicIds = new int[16];

    /**
     * Constructor.
     * @param sessionContainerId              identifier for the session container instance
     * @param sessionByIdMap                  map containing sessions keyed by ID
     * @param fromApplicationSubscription     IPC subscription for messages from the application
     * @param broadcastSubscription           IPC subscription for broadcasts from the application
     * @param sessionAdapterPollFragmentLimit maximum number of messages to process per invocation of the event-loop
     * @param backPressureStrategy            strategy to deal with back-pressure when sending to remote peers
     * @param broadcast                       handle for broadcast messages
     */
    public SessionContainerAdapter(
        final int sessionContainerId,
        final Long2ObjectHashMap<Session> sessionByIdMap,
        final Subscription fromApplicationSubscription,
        final Subscription broadcastSubscription,
        final int sessionAdapterPollFragmentLimit,
        final BackPressureStrategy backPressureStrategy,
        final Broadcast broadcast)
    {
        this.sessionContainerId = sessionContainerId;
        this.sessionByIdMap = sessionByIdMap;
        this.fromApplicationSubscription = fromApplicationSubscription;
        this.broadcastSubscription = broadcastSubscription;
        this.sessionAdapterPollFragmentLimit = sessionAdapterPollFragmentLimit;
        this.backPressureStrategy = backPressureStrategy;
        this.broadcast = broadcast;
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
        switch (messageHeaderDecoder.templateId())
        {
            case ApplicationMessageDecoder.TEMPLATE_ID:

                action = handleApplicationMessage(buffer, offset);
                break;

            case CloseSessionDecoder.TEMPLATE_ID:

                handleCloseMessage(buffer, offset);
                break;

            case CreateTopicDecoder.TEMPLATE_ID:

                createTopicDecoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH,
                    messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());
                broadcast.createTopic(createTopicDecoder.topicId());
                break;

            case DeleteTopicDecoder.TEMPLATE_ID:

                deleteTopicDecoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH,
                    messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());
                broadcast.deleteTopic(deleteTopicDecoder.topicId());
                break;

            case AddSessionToTopicDecoder.TEMPLATE_ID:

                addToTopicDecoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH,
                    messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());
                broadcast.addToTopic(addToTopicDecoder.topicId(), addToTopicDecoder.sessionId());
                break;

            case RemoveSessionFromTopicDecoder.TEMPLATE_ID:

                removeFromTopicDecoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH,
                    messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());
                broadcast.removeFromTopic(removeFromTopicDecoder.topicId(), removeFromTopicDecoder.sessionId());
                break;

            case TopicMessageDecoder.TEMPLATE_ID:

                action = handleTopicMessage(buffer, offset);
                break;

            case MultiTopicMessageDecoder.TEMPLATE_ID:

                action = handleMultiTopicMessage(buffer, offset);
                break;

            default:
                throw new IllegalArgumentException("Unknown templateId: " + messageHeaderDecoder.templateId());
        }

        return action;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int doWork()
    {
        final int sessionWorkDone =
            fromApplicationSubscription.controlledPoll(this, sessionAdapterPollFragmentLimit);
        if (sessionWorkDone == sessionAdapterPollFragmentLimit)
        {
            sessionContainerAdapterStatistics.adapterPollLimitReached();
        }
        int broadcastWorkDone = 0;
        if (broadcastSubscription != null)
        {
            broadcastWorkDone =
                broadcastSubscription.controlledPoll(this, sessionAdapterPollFragmentLimit);
            if (broadcastWorkDone == sessionAdapterPollFragmentLimit)
            {
                sessionContainerAdapterStatistics.adapterPollLimitReached();
            }
        }
        return sessionWorkDone + broadcastWorkDone;
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
    public void sessionAdapterStatistics(final SessionContainerAdapterStatistics statistics)
    {
        this.sessionContainerAdapterStatistics = statistics;
    }

    private Action handleTopicMessage(final DirectBuffer buffer, final int offset)
    {
        final VarDataEncodingDecoder decodedMessage;
        final ContentType contentType;
        topicMessageDecoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH,
            messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());
        contentType = CONTENT_TYPES[topicMessageDecoder.contentType()];
        decodedMessage = topicMessageDecoder.message();
        final int sendResult = broadcast.sendToTopic(
            topicMessageDecoder.topicId(), contentType, decodedMessage.buffer(),
            decodedMessage.offset() + varDataEncodingOffset(), (int)decodedMessage.length());

        if (sendResult == SendResult.BACK_PRESSURE)
        {
            sessionContainerAdapterStatistics.onSessionBackPressure();
        }
        return sendResultToAction(sendResult);
    }


    private Action handleMultiTopicMessage(final DirectBuffer buffer, final int offset)
    {
        final VarDataEncodingDecoder decodedMessage;
        final ContentType contentType;
        multiTopicMessageDecoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH,
            messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());
        contentType = CONTENT_TYPES[multiTopicMessageDecoder.contentType()];
        decodedMessage = multiTopicMessageDecoder.message();
        int topicIdsHeaderLength = BitUtil.SIZE_OF_INT;
        int relativeOffset = decodedMessage.offset() + varDataEncodingOffset();
        final DirectBuffer msgBuffer = decodedMessage.buffer();
        final int topicIdCount = msgBuffer.getInt(relativeOffset);
        relativeOffset += BitUtil.SIZE_OF_INT;
        if (topicIdCount > topicIds.length)
        {
            topicIds = new int[topicIdCount];
        }

        for (int i = 0; i < topicIdCount; i++)
        {
            topicIds[i] = msgBuffer.getInt(relativeOffset);
            relativeOffset += BitUtil.SIZE_OF_INT;
            topicIdsHeaderLength += BitUtil.SIZE_OF_INT;
        }

        final int sendResult = broadcast.sendToTopics(
            topicIds, topicIdCount,
            contentType, msgBuffer,
            relativeOffset, (int)decodedMessage.length() - topicIdsHeaderLength);

        if (sendResult == SendResult.BACK_PRESSURE)
        {
            sessionContainerAdapterStatistics.onSessionBackPressure();
        }
        return sendResultToAction(sendResult);
    }

    private Action handleApplicationMessage(final DirectBuffer buffer, final int offset)
    {
        final VarDataEncodingDecoder decodedMessage;
        final ContentType contentType;
        final long sessionId;
        final Session session;
        applicationMessageDecoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH,
            messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());
        if (applicationMessageDecoder.containerId() == sessionContainerId)
        {
            sessionId = applicationMessageDecoder.sessionId();
            contentType = CONTENT_TYPES[applicationMessageDecoder.contentType()];
            decodedMessage = applicationMessageDecoder.message();
            Logger.log(Category.PROXY, "[%d] SessionContainerAdapter send(sessionId: %d)%n",
                sessionContainerId, sessionId);
            session = sessionByIdMap.get(sessionId);
            if (session != null)
            {
                int sendResult = session.send(contentType, decodedMessage.buffer(),
                    decodedMessage.offset() + varDataEncodingOffset(), (int)decodedMessage.length());
                if (sendResult == SendResult.BACK_PRESSURE)
                {
                    sendResult = backPressureStrategy.onSessionBackPressure(session);
                    sessionContainerAdapterStatistics.onSessionBackPressure();
                }
                return sendResultToAction(sendResult);
            }
        }
        return Action.CONTINUE;
    }

    private void handleCloseMessage(final DirectBuffer buffer, final int offset)
    {
        closeSessionDecoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH,
            messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());
        if (applicationMessageDecoder.containerId() == sessionContainerId)
        {
            final long sessionId = closeSessionDecoder.sessionId();
            final DisconnectReason disconnectReason = DISCONNECT_REASONS[closeSessionDecoder.closeReason()];
            Logger.log(Category.PROXY, "[%d] SessionContainerAdapter close(sessionId: %d)%n",
                sessionContainerId, sessionId);
            final Session session = sessionByIdMap.get(sessionId);
            if (session != null)
            {
                session.close(disconnectReason);
            }
        }
    }
}
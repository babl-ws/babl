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

import com.aitusoftware.babl.codec.AddSessionToTopicEncoder;
import com.aitusoftware.babl.codec.CreateTopicEncoder;
import com.aitusoftware.babl.codec.DeleteTopicEncoder;
import com.aitusoftware.babl.codec.MessageHeaderEncoder;
import com.aitusoftware.babl.codec.MultiTopicMessageEncoder;
import com.aitusoftware.babl.codec.RemoveSessionFromTopicEncoder;
import com.aitusoftware.babl.codec.TopicMessageEncoder;
import com.aitusoftware.babl.codec.VarDataEncodingEncoder;
import com.aitusoftware.babl.log.Category;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.monitoring.ApplicationAdapterStatistics;
import com.aitusoftware.babl.monitoring.BackPressureStatus;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.websocket.broadcast.Broadcast;
import com.aitusoftware.babl.websocket.SendResult;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;

public final class BroadcastProxy implements Broadcast
{
    private static final int HEADER_LENGTH = MessageHeaderEncoder.ENCODED_LENGTH;
    private static final int CREATE_TOPIC_MSG_LENGTH =
        HEADER_LENGTH + CreateTopicEncoder.BLOCK_LENGTH;
    private static final int DELETE_TOPIC_MSG_LENGTH =
        HEADER_LENGTH + DeleteTopicEncoder.BLOCK_LENGTH;
    private static final int ADD_SESSION_TO_TOPIC_MSG_LENGTH =
        HEADER_LENGTH + AddSessionToTopicEncoder.BLOCK_LENGTH;
    private static final int REMOVE_SESSION_FROM_TOPIC_MSG_LENGTH =
        HEADER_LENGTH + RemoveSessionFromTopicEncoder.BLOCK_LENGTH;
    private static final int TOPIC_MESSAGE_BASE_SIZE =
        HEADER_LENGTH + TopicMessageEncoder.BLOCK_LENGTH + BitUtil.SIZE_OF_INT;
    private static final int MULTI_TOPIC_MESSAGE_BASE_SIZE =
        HEADER_LENGTH + MultiTopicMessageEncoder.BLOCK_LENGTH + (2 * BitUtil.SIZE_OF_INT);

    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final CreateTopicEncoder createTopicEncoder = new CreateTopicEncoder();
    private final DeleteTopicEncoder deleteTopicEncoder = new DeleteTopicEncoder();
    private final AddSessionToTopicEncoder addSessionEncoder = new AddSessionToTopicEncoder();
    private final RemoveSessionFromTopicEncoder removeSessionEncoder = new RemoveSessionFromTopicEncoder();
    private final TopicMessageEncoder topicMessageEncoder = new TopicMessageEncoder();
    private final MultiTopicMessageEncoder multiTopicMessageEncoder = new MultiTopicMessageEncoder();
    private final BufferClaim bufferClaim = new BufferClaim();

    private final Publication broadcastPublication;
    private final ApplicationAdapterStatistics applicationAdapterStatistics;

    public BroadcastProxy(
        final Publication broadcastPublication,
        final ApplicationAdapterStatistics applicationAdapterStatistics)
    {
        this.broadcastPublication = broadcastPublication;
        this.applicationAdapterStatistics = applicationAdapterStatistics;
    }

    @Override
    public int createTopic(final int topicId)
    {
        final long result = ProxyUtil.acquireBuffer(CREATE_TOPIC_MSG_LENGTH, broadcastPublication, bufferClaim);
        if (result > 0)
        {
            createTopicEncoder.wrapAndApplyHeader(
                bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder);
            createTopicEncoder.topicId(topicId);
            bufferClaim.commit();
            Logger.log(Category.PROXY, "BroadcastProxy createTopic(topicId: %d)%n",
                topicId);
            applicationAdapterStatistics.proxyBackPressured(BackPressureStatus.NOT_BACK_PRESSURED);
            return SendResult.OK;
        }
        return handleOfferResult(result);
    }


    @Override
    public int deleteTopic(final int topicId)
    {
        final long result = ProxyUtil.acquireBuffer(DELETE_TOPIC_MSG_LENGTH, broadcastPublication, bufferClaim);
        if (result > 0)
        {
            deleteTopicEncoder.wrapAndApplyHeader(
                bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder);
            deleteTopicEncoder.topicId(topicId);
            bufferClaim.commit();
            Logger.log(Category.PROXY, "BroadcastProxy deleteTopic(topicId: %d)%n",
                topicId);
            applicationAdapterStatistics.proxyBackPressured(BackPressureStatus.NOT_BACK_PRESSURED);
            return SendResult.OK;
        }
        return handleOfferResult(result);
    }

    @Override
    public int addToTopic(final int topicId, final long sessionId)
    {
        final long result = ProxyUtil.acquireBuffer(
            ADD_SESSION_TO_TOPIC_MSG_LENGTH, broadcastPublication, bufferClaim);
        if (result > 0)
        {
            addSessionEncoder.wrapAndApplyHeader(
                bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder);
            addSessionEncoder.topicId(topicId);
            addSessionEncoder.sessionId(sessionId);
            bufferClaim.commit();
            Logger.log(Category.PROXY, "BroadcastProxy addToTopic(topicId: %d, sessionId: %d)%n",
                topicId, sessionId);
            applicationAdapterStatistics.proxyBackPressured(BackPressureStatus.NOT_BACK_PRESSURED);
            return SendResult.OK;
        }
        return handleOfferResult(result);
    }

    @Override
    public int removeFromTopic(final int topicId, final long sessionId)
    {
        final long result = ProxyUtil.acquireBuffer(
            REMOVE_SESSION_FROM_TOPIC_MSG_LENGTH, broadcastPublication, bufferClaim);
        if (result > 0)
        {
            removeSessionEncoder.wrapAndApplyHeader(
                bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder);
            removeSessionEncoder.topicId(topicId);
            removeSessionEncoder.sessionId(sessionId);
            bufferClaim.commit();
            Logger.log(Category.PROXY, "BroadcastProxy removeFromTopic(topicId: %d, sessionId: %d)%n",
                topicId, sessionId);
            applicationAdapterStatistics.proxyBackPressured(BackPressureStatus.NOT_BACK_PRESSURED);
            return SendResult.OK;
        }
        return handleOfferResult(result);
    }

    @Override
    public int sendToTopic(
        final int topicId,
        final ContentType contentType,
        final DirectBuffer buffer,
        final int offset,
        final int length)
    {
        final long result = acquireBuffer(
            length + TOPIC_MESSAGE_BASE_SIZE, broadcastPublication, bufferClaim);
        if (result > 0)
        {
            topicMessageEncoder.wrapAndApplyHeader(
                bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder);
            topicMessageEncoder.topicId(topicId);
            topicMessageEncoder.contentType(contentType.ordinal());

            final VarDataEncodingEncoder encodedMessage = topicMessageEncoder.message();
            encodedMessage.length(length);
            encodedMessage.buffer().putBytes(encodedMessage.offset() + varDataEncodingOffset(), buffer, offset, length);
            bufferClaim.commit();
            Logger.log(Category.PROXY, "BroadcastProxy sendToTopic(topicId: %d)%n",
                topicId);
            applicationAdapterStatistics.proxyBackPressured(BackPressureStatus.NOT_BACK_PRESSURED);
            return SendResult.OK;
        }
        return handleOfferResult(result);
    }

    @Override
    public int sendToTopics(
        final int[] topicIds,
        final int idCount,
        final ContentType contentType,
        final DirectBuffer buffer,
        final int offset,
        final int length)
    {
        final int totalLength = MULTI_TOPIC_MESSAGE_BASE_SIZE + length + (BitUtil.SIZE_OF_INT * idCount);
        final long result = acquireBuffer(totalLength, broadcastPublication, bufferClaim);
        if (result > 0)
        {
            multiTopicMessageEncoder.wrapAndApplyHeader(
                bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder);

            multiTopicMessageEncoder.contentType(contentType.ordinal());

            final VarDataEncodingEncoder encodedMessage = multiTopicMessageEncoder.message();
            encodedMessage.length(length + BitUtil.SIZE_OF_INT + (BitUtil.SIZE_OF_INT * (long)idCount));
            final MutableDirectBuffer msgBuffer = encodedMessage.buffer();
            final int initialIndex = encodedMessage.offset() + varDataEncodingOffset();
            int relativeIndex = initialIndex;
            msgBuffer.putInt(initialIndex, idCount);
            relativeIndex += BitUtil.SIZE_OF_INT;
            for (int i = 0; i < idCount; i++)
            {
                msgBuffer.putInt(relativeIndex, topicIds[i]);
                relativeIndex += BitUtil.SIZE_OF_INT;
            }
            msgBuffer.putBytes(relativeIndex, buffer, offset, length);
            bufferClaim.commit();
            Logger.log(Category.PROXY, "BroadcastProxy sendToTopics(count: %d)%n", idCount);
            applicationAdapterStatistics.proxyBackPressured(BackPressureStatus.NOT_BACK_PRESSURED);
            return SendResult.OK;
        }
        return handleOfferResult(result);
    }

    private int handleOfferResult(final long result)
    {
        bufferClaim.abort();
        if (result == Publication.BACK_PRESSURED)
        {
            applicationAdapterStatistics.proxyBackPressure();
            applicationAdapterStatistics.proxyBackPressured(BackPressureStatus.BACK_PRESSURED);
        }

        return ProxyUtil.offerResultToSendResult(result);
    }
}

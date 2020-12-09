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
package com.aitusoftware.babl.websocket.broadcast;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aitusoftware.babl.monitoring.BroadcastStatistics;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.websocket.SendResult;
import com.aitusoftware.babl.websocket.Session;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SessionBroadcastTest
{
    private static final int TOPIC_0 = 28723463;
    private static final int TOPIC_1 = 782367;
    private static final long SESSION_0 = 2893423778L;
    private static final long SESSION_1 = 1233233L;
    private static final long SESSION_2 = 8877828978L;
    private static final ContentType CONTENT_TYPE = ContentType.BINARY;

    private final Long2ObjectHashMap<Session> sessionByIdMap = new Long2ObjectHashMap<>();
    private final BroadcastStatistics statistics = mock(BroadcastStatistics.class);
    private final SessionBroadcast broadcast = new SessionBroadcast(sessionByIdMap, statistics);
    private final UnsafeBuffer messageOne = new UnsafeBuffer(new byte[20]);
    private final UnsafeBuffer messageTwo = new UnsafeBuffer(new byte[20]);

    @BeforeEach
    void setUp()
    {
        sessionByIdMap.put(SESSION_0, mock(Session.class, "session0"));
        sessionByIdMap.put(SESSION_1, mock(Session.class, "session1"));
        sessionByIdMap.put(SESSION_2, mock(Session.class, "session2"));
    }

    @Test
    void shouldCreateTopic()
    {
        assertThat(broadcast.createTopic(TOPIC_0)).isEqualTo(SendResult.OK);
    }

    @Test
    void shouldCreateTopicMultipleTimes()
    {

        assertThat(broadcast.createTopic(TOPIC_0)).isEqualTo(SendResult.OK);
        assertThat(broadcast.createTopic(TOPIC_0)).isEqualTo(SendResult.OK);
    }

    @Test
    void shouldDeleteTopic()
    {
        assertThat(broadcast.createTopic(TOPIC_0)).isEqualTo(SendResult.OK);
        assertThat(broadcast.deleteTopic(TOPIC_0)).isEqualTo(SendResult.OK);
    }

    @Test
    void shouldDeleteTopicMultipleTimes()
    {
        assertThat(broadcast.deleteTopic(TOPIC_0)).isEqualTo(SendResult.OK);
        assertThat(broadcast.deleteTopic(TOPIC_0)).isEqualTo(SendResult.OK);
    }

    @Test
    void shouldAddSessionsToTopic()
    {
        assertThat(broadcast.createTopic(TOPIC_0)).isEqualTo(SendResult.OK);
        assertThat(broadcast.createTopic(TOPIC_1)).isEqualTo(SendResult.OK);

        assertThat(broadcast.addToTopic(TOPIC_0, SESSION_0)).isEqualTo(SendResult.OK);
        assertThat(broadcast.addToTopic(TOPIC_0, SESSION_1)).isEqualTo(SendResult.OK);
        assertThat(broadcast.addToTopic(TOPIC_1, SESSION_2)).isEqualTo(SendResult.OK);
    }

    @Test
    void shouldAddSessionsToTopicMultipleTimes()
    {
        broadcast.createTopic(TOPIC_0);
        assertThat(broadcast.addToTopic(TOPIC_0, SESSION_0)).isEqualTo(SendResult.OK);
        assertThat(broadcast.addToTopic(TOPIC_0, SESSION_0)).isEqualTo(SendResult.OK);
    }

    @Test
    void shouldBroadcastToSessions()
    {
        broadcast.createTopic(TOPIC_0);
        broadcast.createTopic(TOPIC_1);

        broadcast.addToTopic(TOPIC_0, SESSION_0);
        broadcast.addToTopic(TOPIC_0, SESSION_1);
        broadcast.addToTopic(TOPIC_1, SESSION_2);

        broadcast.sendToTopic(TOPIC_0, ContentType.BINARY, messageOne, 0, messageOne.capacity());
        assertMessageDeliveredTo(messageOne, SESSION_0, SESSION_1);

        broadcast.sendToTopic(TOPIC_1, ContentType.BINARY, messageTwo, 0, messageTwo.capacity());
        assertMessageDeliveredTo(messageTwo, SESSION_2);
    }

    @Test
    void shouldCreateTopicIfRequired()
    {
        broadcast.addToTopic(TOPIC_0, SESSION_0);
        broadcast.addToTopic(TOPIC_0, SESSION_1);
        broadcast.sendToTopic(TOPIC_0, CONTENT_TYPE, messageOne, 0, messageOne.capacity());

        assertMessageDeliveredTo(messageOne, SESSION_0, SESSION_1);
    }

    @Test
    void shouldReportSessionSendBackPressure()
    {
        when(sessionByIdMap.get(SESSION_0).send(any(), any(), anyInt(), anyInt()))
            .thenReturn(SendResult.BACK_PRESSURE);

        broadcast.addToTopic(TOPIC_0, SESSION_0);
        broadcast.sendToTopic(TOPIC_0, CONTENT_TYPE, messageOne, 0, messageOne.capacity());

        verify(statistics).broadcastSessionBackPressure();
    }

    @Test
    void shouldReportTopicCount()
    {
        broadcast.addToTopic(TOPIC_0, SESSION_0);
        broadcast.addToTopic(TOPIC_1, SESSION_0);
        broadcast.removeFromTopic(TOPIC_0, SESSION_0);
        broadcast.deleteTopic(TOPIC_1);

        final InOrder order = inOrder(statistics);
        order.verify(statistics).topicCount(1);
        order.verify(statistics).topicCount(2);
        order.verify(statistics).topicCount(1);
    }

    private void assertMessageDeliveredTo(final DirectBuffer buffer, final Long... sessionIds)
    {
        for (final Long sessionId : sessionIds)
        {
            verify(sessionByIdMap.get(sessionId)).send(CONTENT_TYPE, buffer, 0, buffer.capacity());
        }
    }
}
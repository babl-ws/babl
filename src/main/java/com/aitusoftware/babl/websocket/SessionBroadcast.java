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

import com.aitusoftware.babl.monitoring.BroadcastStatistics;
import com.aitusoftware.babl.pool.ObjectPool;
import com.aitusoftware.babl.pool.Pooled;
import com.aitusoftware.babl.user.ContentType;

import org.agrona.DirectBuffer;
import org.agrona.collections.Hashing;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;

final class SessionBroadcast implements Broadcast
{
    private final ObjectPool<PooledSessionSet> sessionSetPool =
        new ObjectPool<>(PooledSessionSet::new, 8);
    private final Int2ObjectHashMap<PooledSessionSet> topicMap =
        new Int2ObjectHashMap<>(64, Hashing.DEFAULT_LOAD_FACTOR);
    private final Long2ObjectHashMap<Session> sessionByIdMap;
    private final BroadcastStatistics statistics;
    private final LongHashSet removalSet = new LongHashSet(128);

    SessionBroadcast(
        final Long2ObjectHashMap<Session> sessionByIdMap,
        final BroadcastStatistics statistics)
    {
        this.sessionByIdMap = sessionByIdMap;
        this.statistics = statistics;
    }

    @Override
    public int createTopic(final int topicId)
    {
        if (!topicMap.containsKey(topicId))
        {
            topicMap.put(topicId, sessionSetPool.acquire());
            statistics.topicCount(topicMap.size());
        }
        return SendResult.OK;
    }

    @Override
    public int deleteTopic(final int topicId)
    {
        final PooledSessionSet sessionSet = topicMap.remove(topicId);
        if (sessionSet != null)
        {
            sessionSetPool.release(sessionSet);
            statistics.topicCount(topicMap.size());
        }
        return SendResult.OK;
    }

    @Override
    public int addToTopic(final int topicId, final long sessionId)
    {
        PooledSessionSet sessionSet = topicMap.get(topicId);
        if (sessionSet == null)
        {
            sessionSet = sessionSetPool.acquire();
            topicMap.put(topicId, sessionSet);
            statistics.topicCount(topicMap.size());
        }
        sessionSet.set.add(sessionId);
        return SendResult.OK;
    }

    @Override
    public int removeFromTopic(final int topicId, final long sessionId)
    {
        final PooledSessionSet sessionSet = topicMap.get(topicId);
        if (sessionSet != null)
        {
            sessionSet.set.remove(sessionId);
        }
        return SendResult.OK;
    }

    @Override
    public int sendToTopic(
        final int topicId,
        final ContentType contentType,
        final DirectBuffer buffer,
        final int offset,
        final int length)
    {
        final PooledSessionSet sessionSet = topicMap.get(topicId);
        if (sessionSet != null)
        {
            removalSet.clear();
            final LongHashSet.LongIterator iterator = sessionSet.set.iterator();
            while (iterator.hasNext())
            {
                final long sessionId = iterator.nextValue();
                final Session session = sessionByIdMap.get(sessionId);
                if (session != null)
                {
                    if (SendResult.BACK_PRESSURE == session.send(contentType, buffer, offset, length))
                    {
                        statistics.broadcastSessionBackPressure();
                    }
                }
                else
                {
                    removalSet.add(sessionId);
                }
            }
            if (!removalSet.isEmpty())
            {
                sessionSet.set.removeAll(removalSet);
            }
        }

        return SendResult.OK;
    }

    private static final class PooledSessionSet implements Pooled
    {
        private final LongHashSet set = new LongHashSet(128, Hashing.DEFAULT_LOAD_FACTOR);

        @Override
        public void reset()
        {
            set.clear();
        }
    }
}

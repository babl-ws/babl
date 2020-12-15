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

import com.aitusoftware.babl.user.ContentType;

import org.agrona.DirectBuffer;

/**
 * An interface for sending broadcast messages to topics.
 */
public interface Broadcast
{
    /**
     * Create a new topic.
     * @param topicId the topic ID
     * @return a {@code SendResult}
     */
    int createTopic(int topicId);

    /**
     * Delete a topic.
     * @param topicId the topic ID
     * @return a {@code SendResult}
     */
    int deleteTopic(int topicId);

    /**
     * Add a session to a topic.
     * @param topicId the topic ID
     * @param sessionId the session ID to add
     * @return a {@code SendResult}
     */
    int addToTopic(int topicId, long sessionId);

    /**
     * Remove a session from a topic.
     * @param topicId the topic ID
     * @param sessionId the session ID to remove
     * @return a {@code SendResult}
     */
    int removeFromTopic(int topicId, long sessionId);

    /**
     * Send a message to a topic.
     *
     * @param topicId the topic ID
     * @param contentType the message content type
     * @param buffer the buffer
     * @param offset the offset into the buffer
     * @param length the length of the message
     * @return a {@code SendResult}
     */
    int sendToTopic(int topicId, ContentType contentType, DirectBuffer buffer, int offset, int length);

    /**
     * Send a message to multiple topics.
     *
     * @param topicIds the topic IDs
     * @param idCount the number of topic IDs in the array
     * @param contentType the message content type
     * @param buffer the buffer
     * @param offset the offset into the buffer
     * @param length the length of the message
     * @return a {@code SendResult}
     */
    int sendToTopics(
        int[] topicIds,
        int idCount,
        ContentType contentType,
        DirectBuffer buffer,
        int offset,
        int length);
}

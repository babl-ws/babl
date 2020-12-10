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


import org.agrona.DirectBuffer;

/**
 * User code should implement this interface to translate messages
 * for a given topic before broadcast to the sessions within the topic.
 */
public interface MessageTransformer
{
    /**
     * Translate an input message before it is broadcast.
     *
     * @param topicId the topic ID
     * @param input the input message from the application
     * @param offset the offset into the buffer
     * @param length the length of the message
     * @return the translated message intended for broadcast
     */
    TransformResult transform(
        int topicId,
        DirectBuffer input,
        int offset,
        int length);
}

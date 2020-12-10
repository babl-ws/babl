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
 * Super-class for {@code MessageTransformer} implementations.
 */
public abstract class AbstractMessageTransformer implements MessageTransformer
{
    private final TransformResult transformResult = new TransformResult();

    @Override
    public TransformResult transform(
        final int topicId,
        final DirectBuffer input,
        final int offset,
        final int length)
    {
        if (!doTransform(topicId, input, offset, length, transformResult))
        {
            transformResult.set(input, offset, length);
        }
        return transformResult;
    }

    /**
     * Implementations should return {@code true} if the message was transformed,
     * otherwise, the original message will be returned.
     *
     * @param topicId the topic ID
     * @param input the input message
     * @param offset the offset into the input buffer
     * @param length the length of the input message
     * @param result a container for the resulting transformed message
     * @return whether the message was transformed from its original form
     */
    protected abstract boolean doTransform(
        int topicId,
        DirectBuffer input,
        int offset,
        int length,
        TransformResult result);
}

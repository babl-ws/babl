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
package com.aitusoftware.babl.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import io.vertx.core.Handler;
import io.vertx.core.http.WebSocketFrame;

final class ClientData implements Handler<WebSocketFrame>
{
    final CountDownLatch latch;
    final List<String> messagesSent = new ArrayList<>();
    final List<String> messagesReceived = new ArrayList<>();
    final List<String> otherMessagesReceived = new ArrayList<>();

    ClientData(final int messageCount)
    {
        latch = new CountDownLatch(messageCount);
    }

    @Override
    public void handle(final WebSocketFrame event)
    {
        if (event.isFinal())
        {
            final String textData = event.textData();
            if (textData.startsWith("payload"))
            {
                messagesReceived.add(textData);
                latch.countDown();
            }
            else
            {
                otherMessagesReceived.add(textData);
            }
        }
    }
}

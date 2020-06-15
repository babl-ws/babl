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

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Used to perform validation on a new connection.
 * Called after a web-socket upgrade has been completed.
 *
 * Implementations MUST NOT block.
 */
public interface ConnectionValidator
{
    /**
     * Called from the event-loop after an upgrade has been processed.
     * <p>
     * Implementations MUST NOT block, and should publish the result of the validation to the supplied
     * <code>ValidationResultPublisher</code>.
     *
     * @param validationResult a container for the result of validation - implementations must not retain a reference
     * @param headerProvider a callback for obtaining and HTTP headers supplied by the client
     * @param validationResultPublisher a thread-safe publisher for notifying the server of the validation result
     */
    void validateConnection(
        ValidationResult validationResult,
        Consumer<BiConsumer<CharSequence, CharSequence>> headerProvider,
        ValidationResultPublisher validationResultPublisher);

    /**
     * Can optionally do some work on the event-loop thread.
     *
     * @return amount of work done
     */
    default int doWork()
    {
        return 0;
    }
}
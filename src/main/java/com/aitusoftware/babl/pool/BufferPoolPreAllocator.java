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
package com.aitusoftware.babl.pool;

/**
 * A supplied instance of this class will be invoked before
 * server start-up. Implementations should {@code acquire()} and
 * {@code release()} the expected number and size of buffers
 * so that latency is not incurred performing these allocations
 * once the server is processing connections.
 */
public interface BufferPoolPreAllocator
{
    /**
     * Called before server start-up, allows an implementation to
     * pre-allocate expected required buffers.
     *
     * @param bufferPool the {@code BufferPool} used by the server
     */
    void preAllocate(BufferPool bufferPool);
}
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

import static com.google.common.truth.Truth.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.aitusoftware.babl.pool.ObjectPool;

import org.junit.jupiter.api.Test;

class ConnectionUpgradeTest
{
    private static final ByteBuffer INPUT = ByteBuffer.wrap(
        KeyDecoderTest.MIXED_CASE_HEADER.getBytes(StandardCharsets.UTF_8));
    private final ConnectionUpgrade connectionUpgrade =
        new ConnectionUpgrade(new ObjectPool<>(ValidationResult::new, 8),
        new AlwaysValidConnectionValidator(), (b) -> true);

    @Test
    void shouldHandleConnectionUpgrade()
    {
        final ByteBuffer output = ByteBuffer.allocate(256);
        connectionUpgrade.handleUpgrade(INPUT, output);

        assertThat(new String(output.array(), 0, output.remaining())).contains(
            "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: dYmglNOBLQdZMTi1zvufMrlbcZI=");
    }
}
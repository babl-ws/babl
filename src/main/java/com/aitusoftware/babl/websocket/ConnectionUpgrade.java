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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import com.aitusoftware.babl.log.Category;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.pool.ObjectPool;
import com.aitusoftware.babl.pool.Pooled;

class ConnectionUpgrade implements Pooled
{
    private static final byte[] RESPONSE_MESSAGE_START =
        ("HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: ").getBytes(StandardCharsets.UTF_8);
    private static final byte[] RESPONSE_MESSAGE_END =
        "\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] WEB_SOCKET_HANDSHAKE_MAGIC_VALUE =
        "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(StandardCharsets.UTF_8);

    private final KeyDecoder keyDecoder;
    private final MessageDigest sha1;
    private final ObjectPool<ValidationResult> validationResultPool;
    private final ConnectionValidator connectionValidator;
    private final ValidationResultPublisher validationResultPublisher;
    private ByteBuffer handshakeResponseOutputBuffer;
    private long sessionId;

    ConnectionUpgrade(
        final ObjectPool<ValidationResult> validationResultPool,
        final ConnectionValidator connectionValidator,
        final ValidationResultPublisher validationResultPublisher)
    {
        this.validationResultPool = validationResultPool;
        this.connectionValidator = connectionValidator;
        this.validationResultPublisher = validationResultPublisher;
        this.keyDecoder = new KeyDecoder();
        try
        {
            sha1 = MessageDigest.getInstance("SHA-1");
        }
        catch (final NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("Expected required algorithm SHA-1 to be supported on JVM");
        }
    }

    boolean handleUpgrade(final ByteBuffer input, final ByteBuffer output)
    {
        handshakeResponseOutputBuffer = output;
        final boolean decoded = keyDecoder.decode(input, this::writeUpgradeResponse);
        if (decoded)
        {
            Logger.log(Category.CONNECTION, "Session %d key decoded%n", sessionId);
            final ValidationResult validationResult = validationResultPool.acquire();
            validationResult.sessionId(sessionId);
            connectionValidator.validateConnection(validationResult, keyDecoder, validationResultPublisher);
        }
        return decoded;
    }

    void init(final long sessionId)
    {
        this.sessionId = sessionId;
    }

    @Override
    public void reset()
    {
        keyDecoder.reset();
    }

    private void writeUpgradeResponse(final CharSequence key)
    {
        Logger.log(Category.CONNECTION, "Session %d web-socket key: %s%n", sessionId, key);
        sha1.reset();
        for (int i = 0; i < key.length(); i++)
        {
            sha1.update((byte)key.charAt(i));
        }
        sha1.update(WEB_SOCKET_HANDSHAKE_MAGIC_VALUE);
        final byte[] hashed = sha1.digest();
        handshakeResponseOutputBuffer.clear();
        handshakeResponseOutputBuffer.put(RESPONSE_MESSAGE_START);

        handshakeResponseOutputBuffer.put(Base64.getEncoder().encode(hashed));
        handshakeResponseOutputBuffer.put(RESPONSE_MESSAGE_END);
        handshakeResponseOutputBuffer = null;
    }
}
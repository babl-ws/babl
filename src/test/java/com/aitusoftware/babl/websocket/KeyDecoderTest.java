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

import org.junit.jupiter.api.Test;

class KeyDecoderTest
{
    private static final String WEB_SOCKET_KEY = "O+1mTBi8AaLSEkXvL1bAaA==";
    static final String MIXED_CASE_HEADER = "" +
        "GET / HTTP/1.1\r\n" +
        "Host: 127.0.0.1:8080\r\n" +
        "User-Agent: MegaBrowser 1.0\r\n" +
        "Accept: */*\r\n" +
        "Accept-Language: en-US,en;q=0.5\r\n" +
        "Accept-Encoding: gzip, deflate\r\n" +
        "Sec-WebSocket-Version: 13\r\n" +
        "Origin: null\r\n" +
        "Sec-WebSocket-Extensions: permessage-deflate\r\n" +
        "Sec-WebSocket-Key: " +
        WEB_SOCKET_KEY +
        "\r\n" +
        "Connection: keep-alive, Upgrade\r\n" +
        "Pragma: no-cache\r\n" +
        "Cache-Control: no-cache\r\n" +
        "Upgrade: websocket\r\n" +
        "\r\n";

    private static final String UPPER_CASE_HEADER = "" +
        "GET / HTTP/1.1\r\n" +
        "HOST: 127.0.0.1:8080\r\n" +
        "USER-AGENT: MegaBrowser 1.0\r\n" +
        "ACCEPT: */*\r\n" +
        "ACCEPT-LANGUAGE: en-US,en;q=0.5\r\n" +
        "ACCEPT-ENCODING: gzip, deflate\r\n" +
        "SEC-WEBSOCKET-VERSION: 13\r\n" +
        "ORIGIN: null\r\n" +
        "SEC-WEBSOCKET-EXTENSIONS: permessage-deflate\r\n" +
        "SEC-WEBSOCKET-KEY: " +
        WEB_SOCKET_KEY +
        "\r\n" +
        "CONNECTION: keep-alive, Upgrade\r\n" +
        "PRAGMA: no-cache\r\n" +
        "CACHE-CONTROL: no-cache\r\n" +
        "UPGRADE: websocket\r\n" +
        "\r\n";

    private static final String LOWER_CASE_HEADER = "" +
        "GET / HTTP/1.1\r\n" +
        "host: 127.0.0.1:8080\r\n" +
        "user-agent: MegaBrowser 1.0\r\n" +
        "accept: */*\r\n" +
        "accept-language: en-US,en;q=0.5\r\n" +
        "accept-encoding: gzip, deflate\r\n" +
        "sec-websocket-version: 13\r\n" +
        "origin: null\r\n" +
        "sec-websocket-extensions: permessage-deflate\r\n" +
        "sec-websocket-key: " +
        WEB_SOCKET_KEY +
        "\r\n" +
        "connection: keep-alive, Upgrade\r\n" +
        "pragma: no-cache\r\n" +
        "cache-control: no-cache\r\n" +
        "upgrade: websocket\r\n" +
        "\r\n";

    private static final String NO_UPGRADE_WEBSOCKET_HEADER = "" +
        "GET / HTTP/1.1\r\n" +
        "host: 127.0.0.1:8080\r\n" +
        "user-agent: MegaBrowser 1.0\r\n" +
        "accept: */*\r\n" +
        "accept-language: en-US,en;q=0.5\r\n" +
        "accept-encoding: gzip, deflate\r\n" +
        "sec-websocket-version: 13\r\n" +
        "origin: null\r\n" +
        "sec-websocket-extensions: permessage-deflate\r\n" +
        "sec-websocket-key: " +
        WEB_SOCKET_KEY +
        "\r\n" +
        "connection: keep-alive, Upgrade\r\n" +
        "pragma: no-cache\r\n" +
        "cache-control: no-cache\r\n" +
        "\r\n";

    private static final String NO_CONNECTION_UPGRADE_HEADER = "" +
        "GET / HTTP/1.1\r\n" +
        "host: 127.0.0.1:8080\r\n" +
        "user-agent: MegaBrowser 1.0\r\n" +
        "accept: */*\r\n" +
        "accept-language: en-US,en;q=0.5\r\n" +
        "accept-encoding: gzip, deflate\r\n" +
        "sec-websocket-version: 13\r\n" +
        "origin: null\r\n" +
        "sec-websocket-extensions: permessage-deflate\r\n" +
        "sec-websocket-key: " +
        WEB_SOCKET_KEY +
        "\r\n" +
        "pragma: no-cache\r\n" +
        "upgrade: websocket\r\n" +
        "cache-control: no-cache\r\n" +
        "\r\n";

    private final KeyDecoder keyDecoder = new KeyDecoder();
    private byte[] capturedKey;

    @Test
    void shouldSetBufferPositionAtEndOfHttpRequest()
    {
        assertKeyDecoded(UPPER_CASE_HEADER + "some trailing bytes", true);
    }

    @Test
    void shouldRequireUpgradeWebSocketHeader()
    {
        assertKeyDecoded(NO_UPGRADE_WEBSOCKET_HEADER, false);
    }

    @Test
    void shouldRequireConnectionUpgradeHeader()
    {
        assertKeyDecoded(NO_CONNECTION_UPGRADE_HEADER, false);
    }

    @Test
    void shouldDecodeMixedCaseHeader()
    {
        assertKeyDecoded(MIXED_CASE_HEADER, true);
    }

    @Test
    void shouldDecodeUpperCaseHeader()
    {
        assertKeyDecoded(UPPER_CASE_HEADER, true);
    }

    @Test
    void shouldDecodeLowerCaseHeader()
    {
        assertKeyDecoded(LOWER_CASE_HEADER, true);
    }

    private void assertKeyDecoded(final String httpRequest, final boolean shouldBeDecoded)
    {
        final ByteBuffer buffer = ByteBuffer.wrap(httpRequest.getBytes(StandardCharsets.UTF_8));
        assertThat(keyDecoder.decode(
            buffer, this::captureCharKey))
            .isEqualTo(shouldBeDecoded);
        if (shouldBeDecoded)
        {
            assertThat(capturedKey).isEqualTo(WEB_SOCKET_KEY.getBytes(StandardCharsets.UTF_8));
        }
        else
        {
            assertThat(capturedKey).isNull();
        }
        assertThat(buffer.position()).isEqualTo(httpRequest.indexOf("\r\n\r\n") + 4);
    }

    private void captureCharKey(final CharSequence key)
    {
        capturedKey = key.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
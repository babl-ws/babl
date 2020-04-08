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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.aitusoftware.babl.pool.ObjectPool;
import com.aitusoftware.babl.pool.Pooled;

final class KeyDecoder implements Consumer<BiConsumer<CharSequence, CharSequence>>
{
    private static final String SEC_WEBSOCKET_KEY_HEADER = "sec-websocket-key";
    //upgrade: websocket
    private static final String UPGRADE_HEADER = "upgrade";
    //connection: keep-alive, Upgrade
    private static final String CONNECTION_HEADER = "connection";
    //sec-websocket-version: 13
    private static final String SEC_WEBSOCKET_VERSION_HEADER = "sec-websocket-version";
    private static final String GET_REQUEST = "get /";
    private static final String WEBSOCKET_PATTERN = "websocket";
    private static final String UPGRADE_PATTERN = "upgrade";

    private final StringBuilder accumulator = new StringBuilder();
    private final StringBuilder secWebSocketKey = new StringBuilder();
    private final StringBuilder unhandledHeaderKey = new StringBuilder();
    private final ObjectPool<HttpHeader> httpHeaderPool = new ObjectPool<>(HttpHeader::new, 8);
    private final List<HttpHeader> httpHeaders = new ArrayList<>();
    private Header header = Header.UNHANDLED;

    void reset()
    {
        accumulator.setLength(0);
        unhandledHeaderKey.setLength(0);
        secWebSocketKey.setLength(0);
        header = Header.UNHANDLED;
        httpHeaders.forEach(httpHeaderPool::release);
        httpHeaders.clear();
    }

    boolean decode(
        final ByteBuffer input,
        final Consumer<CharSequence> webSocketSecKeyHandler)
    {
        State state = State.READ_HEADER_KEY;
        boolean keyDecoded = false;
        boolean connectionHeaderPresent = false;
        boolean upgradeHeaderPresent = false;
        boolean startsWithGetRequest = false;
        int endOfHttpRequest = 0;
        for (int i = input.position(); i < input.remaining(); i++)
        {
            if (endOfHttpRequest != 0)
            {
                break;
            }
            final byte current = input.get(i);
            switch (state)
            {
                case READ_HEADER_KEY:
                    if (current == '\r' && canRead(input, i + 1) && next(input, i) == '\n')
                    {
                        if (accumulator.length() == 0)
                        {
                            endOfHttpRequest = i + 2;
                            break;
                        }
                        if (charSequencesEqual(GET_REQUEST, accumulator, GET_REQUEST.length()))
                        {
                            startsWithGetRequest = true;
                        }
                        i++;
                        accumulator.setLength(0);
                    }
                    else if (current == ':')
                    {
                        assignHeader();
                        accumulator.setLength(0);
                        state = State.READ_HEADER_VALUE;
                    }
                    else
                    {
                        accumulator.append(Character.toLowerCase((char)current));
                    }
                    break;
                case READ_HEADER_VALUE:
                    if (current == '\r' && canRead(input, i + 1) && next(input, i) == '\n')
                    {
                        i++;
                        switch (header)
                        {
                            case SEC_WEBSOCKET_KEY:
                                secWebSocketKey.append(accumulator);
                                keyDecoded = true;
                                break;
                            case UPGRADE:
                                upgradeHeaderPresent = charSequenceContainsLowerCase(accumulator, WEBSOCKET_PATTERN);
                                break;
                            case CONNECTION:
                                connectionHeaderPresent = charSequenceContainsLowerCase(accumulator, UPGRADE_PATTERN);
                                break;
                            case UNHANDLED:
                                final HttpHeader httpHeader = httpHeaderPool.acquire();
                                httpHeader.key.append(unhandledHeaderKey);
                                httpHeader.value.append(accumulator);
                                httpHeaders.add(httpHeader);
                                break;
                        }
                        accumulator.setLength(0);
                        state = State.READ_HEADER_KEY;
                    }
                    else
                    {
                        if (accumulator.length() != 0 || current != ' ')
                        {
                            accumulator.append((char)current);
                        }
                    }
                    break;
            }
        }
        if (endOfHttpRequest != 0)
        {
            input.position(endOfHttpRequest);
        }

        final boolean processingCompleted = startsWithGetRequest && keyDecoded && connectionHeaderPresent &&
            upgradeHeaderPresent && endOfHttpRequest != 0;
        if (processingCompleted)
        {
            webSocketSecKeyHandler.accept(secWebSocketKey);
        }
        return processingCompleted;
    }

    @Override
    public void accept(final BiConsumer<CharSequence, CharSequence> headerAcceptor)
    {
        for (int i = 0; i < httpHeaders.size(); i++)
        {
            final HttpHeader httpHeader = httpHeaders.get(i);
            headerAcceptor.accept(httpHeader.key, httpHeader.value);
        }
    }

    private void assignHeader()
    {
        if (charSequencesEqual(SEC_WEBSOCKET_KEY_HEADER, accumulator))
        {
            header = Header.SEC_WEBSOCKET_KEY;
        }
        else if (charSequencesEqual(UPGRADE_HEADER, accumulator))
        {
            header = Header.UPGRADE;
        }
        else if (charSequencesEqual(SEC_WEBSOCKET_VERSION_HEADER, accumulator))
        {
            header = Header.SEC_WEBSOCKET_VERSION;
        }
        else if (charSequencesEqual(CONNECTION_HEADER, accumulator))
        {
            header = Header.CONNECTION;
        }
        else
        {
            unhandledHeaderKey.setLength(0);
            unhandledHeaderKey.append(accumulator);
            header = Header.UNHANDLED;
        }
    }

    private boolean canRead(final ByteBuffer buffer, final int position)
    {
        return position < buffer.limit();
    }

    private byte next(final ByteBuffer buffer, final int currentPosition)
    {
        return buffer.get(currentPosition + 1);
    }

    private enum Header
    {
        SEC_WEBSOCKET_KEY,
        SEC_WEBSOCKET_VERSION,
        UPGRADE,
        CONNECTION,
        UNHANDLED
    }

    private enum State
    {
        READ_HEADER_KEY,
        READ_HEADER_VALUE
    }

    private static boolean charSequencesEqual(final CharSequence a, final CharSequence b, final int maxLength)
    {
        if (a.length() < maxLength || b.length() < maxLength)
        {
            return false;
        }
        for (int i = 0; i < maxLength; i++)
        {
            if (a.charAt(i) != b.charAt(i))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean charSequencesEqual(final CharSequence a, final CharSequence b)
    {
        if (a.length() != b.length())
        {
            return false;
        }

        return charSequencesEqual(a, b, Math.min(a.length(), b.length()));
    }

    private static boolean charSequenceContainsLowerCase(final CharSequence source, final CharSequence pattern)
    {
        if (source.length() < pattern.length())
        {
            return false;
        }
        int patternIndex = 0;
        for (int i = 0; i < source.length(); i++)
        {
            if (Character.toLowerCase(source.charAt(i)) == pattern.charAt(patternIndex))
            {
                patternIndex++;
            }
            else if (patternIndex < pattern.length())
            {
                patternIndex = 0;
            }
        }
        return patternIndex == pattern.length();
    }

    private static final class HttpHeader implements Pooled
    {
        private final StringBuilder key = new StringBuilder();
        private final StringBuilder value = new StringBuilder();

        @Override
        public void reset()
        {
            key.setLength(0);
            value.setLength(0);
        }
    }
}
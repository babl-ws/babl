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
package com.aitusoftware.babl.monitoring;

import com.aitusoftware.babl.pool.Pooled;
import com.aitusoftware.babl.websocket.WebSocketSession;

import org.agrona.BitUtil;
import org.agrona.concurrent.AtomicBuffer;

public final class MappedSessionStatistics extends SessionStatistics implements Pooled
{
    private static final int SESSION_ID_OFFSET = 0;
    private static final int SESSION_STATE_OFFSET = SESSION_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int BYTES_READ_OFFSET = SESSION_STATE_OFFSET + BitUtil.SIZE_OF_INT;
    private static final int BYTES_WRITTEN_OFFSET = BYTES_READ_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int RECEIVE_BUFFERED_BYTES_OFFSET = BYTES_WRITTEN_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int SEND_BUFFERED_BYTES_OFFSET = RECEIVE_BUFFERED_BYTES_OFFSET + BitUtil.SIZE_OF_INT;
    private static final int FRAMES_DECODED_OFFSET = SEND_BUFFERED_BYTES_OFFSET + BitUtil.SIZE_OF_INT;
    private static final int MESSAGES_RECEIVED_OFFSET = FRAMES_DECODED_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int FRAMES_ENCODED_OFFSET = MESSAGES_RECEIVED_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int MESSAGES_SENT_OFFSET = FRAMES_ENCODED_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int INVALID_MESSAGES_RECEIVED_OFFSET = MESSAGES_SENT_OFFSET + BitUtil.SIZE_OF_LONG;
    private static final int INVALID_PINGS_RECEIVED_OFFSET = INVALID_MESSAGES_RECEIVED_OFFSET + BitUtil.SIZE_OF_INT;
    private static final int SEND_BACK_PRESSURE_EVENTS_OFFSET = INVALID_PINGS_RECEIVED_OFFSET + BitUtil.SIZE_OF_INT;
    private static final int REMOTE_ADDRESS_OFFSET = SEND_BACK_PRESSURE_EVENTS_OFFSET + BitUtil.SIZE_OF_LONG;
    public static final int LENGTH = REMOTE_ADDRESS_OFFSET + (BitUtil.SIZE_OF_LONG * 2);
    public static final int NULL_SESSION_ID = 0;

    private AtomicBuffer buffer;
    private int offset;

    private long sessionId;
    private WebSocketSession.SessionState sessionState;

    private long bytesRead;
    private long bytesWritten;
    private long framesDecoded;
    private long messagesReceived;
    private long framesEncoded;
    private long messagesSent;
    private int invalidMessagesReceived;
    private int invalidPingsReceived;
    private long sendBackPressureEvents;


    public void set(
        final AtomicBuffer buffer,
        final int offset,
        final long sessionId,
        final WebSocketSession.SessionState sessionState)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.sessionId = sessionId;


        init(sessionId, sessionState);
    }

    public void wrap(final AtomicBuffer buffer, final int offset)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.sessionId = -1L;
    }

    @Override
    public void reset(final long sessionId, final WebSocketSession.SessionState sessionState)
    {
        init(sessionId, WebSocketSession.SessionState.CONNECTED);
    }

    public long bytesRead()
    {
        return buffer.getLongVolatile(toOffset(BYTES_READ_OFFSET));
    }

    public long bytesWritten()
    {
        return buffer.getLongVolatile(toOffset(BYTES_WRITTEN_OFFSET));
    }

    public long framesDecoded()
    {
        return buffer.getLongVolatile(toOffset(FRAMES_DECODED_OFFSET));
    }

    public long framesEncoded()
    {
        return buffer.getLongVolatile(toOffset(FRAMES_ENCODED_OFFSET));
    }

    public long messagesReceived()
    {
        return buffer.getLongVolatile(toOffset(MESSAGES_RECEIVED_OFFSET));
    }

    public long messagesSent()
    {
        return buffer.getLongVolatile(toOffset(MESSAGES_SENT_OFFSET));
    }

    public int invalidMessagesReceived()
    {
        return buffer.getIntVolatile(toOffset(INVALID_MESSAGES_RECEIVED_OFFSET));
    }

    public int invalidPingsReceived()
    {
        return buffer.getIntVolatile(toOffset(INVALID_PINGS_RECEIVED_OFFSET));
    }

    public long sendBackPressureEvents()
    {
        return buffer.getLongVolatile(toOffset(SEND_BACK_PRESSURE_EVENTS_OFFSET));
    }

    public int receiveBufferedBytes()
    {
        return buffer.getIntVolatile(toOffset(RECEIVE_BUFFERED_BYTES_OFFSET));
    }

    public int sendBufferedBytes()
    {
        return buffer.getIntVolatile(toOffset(SEND_BUFFERED_BYTES_OFFSET));
    }

    public long sessionId()
    {
        return buffer.getLongVolatile(toOffset(SESSION_ID_OFFSET));
    }

    public WebSocketSession.SessionState sessionState()
    {
        return WebSocketSession.SessionState.values()[buffer.getIntVolatile(toOffset(SESSION_STATE_OFFSET))];
    }

    @Override
    public void bytesRead(final int bytesRead)
    {
        if (bytesRead > 0)
        {
            this.bytesRead += bytesRead;
            buffer.putLongOrdered(toOffset(BYTES_READ_OFFSET), this.bytesRead);
        }
    }

    @Override
    public void bytesWritten(final int bytesWritten)
    {
        if (bytesWritten > 0)
        {
            this.bytesWritten += bytesWritten;
            buffer.putLongOrdered(toOffset(BYTES_WRITTEN_OFFSET), this.bytesWritten);
        }
    }

    @Override
    public void receiveBufferedBytes(final int receiveBufferedBytes)
    {
        buffer.putIntOrdered(toOffset(RECEIVE_BUFFERED_BYTES_OFFSET), receiveBufferedBytes);
    }

    @Override
    public void sendBufferedBytes(final int sendBufferedBytes)
    {
        buffer.putIntOrdered(toOffset(SEND_BUFFERED_BYTES_OFFSET), sendBufferedBytes);
    }

    @Override
    public void frameDecoded()
    {
        framesDecoded++;
        buffer.putLongOrdered(toOffset(FRAMES_DECODED_OFFSET), framesDecoded);
    }

    @Override
    public void messageReceived()
    {
        messagesReceived++;
        buffer.putLongOrdered(toOffset(MESSAGES_RECEIVED_OFFSET), messagesReceived);
    }

    @Override
    public void frameEncoded()
    {
        framesEncoded++;
        buffer.putLongOrdered(toOffset(FRAMES_ENCODED_OFFSET), framesEncoded);
    }

    @Override
    public void messageSent()
    {
        messagesSent++;
        buffer.putLongOrdered(toOffset(MESSAGES_SENT_OFFSET), messagesSent);
    }

    @Override
    public void invalidMessageReceived()
    {
        invalidMessagesReceived++;
        buffer.putIntOrdered(toOffset(INVALID_MESSAGES_RECEIVED_OFFSET), invalidMessagesReceived);
    }

    @Override
    public void invalidPingReceived()
    {
        invalidPingsReceived++;
        buffer.putIntOrdered(toOffset(INVALID_PINGS_RECEIVED_OFFSET), invalidPingsReceived);
    }

    @Override
    public void sendBackPressure()
    {
        sendBackPressureEvents++;
        buffer.putLongOrdered(toOffset(SEND_BACK_PRESSURE_EVENTS_OFFSET), sendBackPressureEvents);
    }

    public int offset()
    {
        return offset;
    }

    public void removed()
    {
        buffer.putLongOrdered(toOffset(SESSION_ID_OFFSET), NULL_SESSION_ID);
    }

    private int toOffset(final int offset)
    {
        return this.offset + offset;
    }

    @Override
    public void reset()
    {
        // no-op
    }

    private void init(final long sessionId, final WebSocketSession.SessionState sessionState)
    {
        bytesRead = 0;
        bytesWritten = 0;
        framesDecoded = 0;
        framesEncoded = 0;
        messagesReceived = 0;
        messagesSent = 0;
        invalidMessagesReceived = 0;
        invalidPingsReceived = 0;
        sendBackPressureEvents = 0;
        receiveBufferedBytes(0);
        sendBufferedBytes(0);
        buffer.putLongOrdered(toOffset(SESSION_ID_OFFSET), sessionId);
        buffer.putIntOrdered(toOffset(SESSION_STATE_OFFSET), sessionState.ordinal());
    }
}
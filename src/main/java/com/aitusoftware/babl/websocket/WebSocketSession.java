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

import static com.aitusoftware.babl.io.BufferUtil.increaseCapacity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

import com.aitusoftware.babl.log.Category;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.monitoring.SessionContainerStatistics;
import com.aitusoftware.babl.monitoring.SessionStatistics;
import com.aitusoftware.babl.pool.BufferPool;
import com.aitusoftware.babl.pool.Pooled;
import com.aitusoftware.babl.user.Application;
import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.config.SessionConfig;

import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;

/**
 * Models a web socket session; used by an {@code Application} to send messages to a peer.
 */
public final class WebSocketSession implements Pooled, Session
{
    private static final int MAX_FRAMES = 4;
    private static final int UPGRADE_SEND_BUFFER_SIZE = 1024;
    private static final short NULL_CLOSE_REASON = Short.MIN_VALUE;

    private final FrameDecoder frameDecoder;
    private final FrameEncoder frameEncoder;
    private final SessionConfig sessionConfig;
    private final SessionDataListener sessionDataListener;
    private final BufferPool bufferPool;
    private final Application application;
    private final PingAgent pingAgent;
    private final SessionContainerStatistics sessionContainerStatistics;

    private long id;
    private SocketChannel channel;
    private SessionStatistics sessionStatistics;
    private transient ByteBuffer handshakeSendBuffer;
    private ByteBuffer receiveBuffer;
    private SessionState state = SessionState.UPGRADING;
    private boolean closed;
    private short closeReason = NULL_CLOSE_REASON;
    private ConnectionUpgrade connectionUpgrade;
    private Consumer<ConnectionUpgrade> connectionUpgradeReleaseHandler;
    private DisconnectReason undeliveredDisconnectReason;
    private long connectedTimestampMs;

    WebSocketSession(
        final SessionDataListener sessionDataListener,
        final FrameDecoder frameDecoder,
        final FrameEncoder frameEncoder,
        final SessionConfig sessionConfig,
        final BufferPool bufferPool,
        final Application application,
        final PingAgent pingAgent,
        final SessionStatistics sessionStatistics,
        final SessionContainerStatistics sessionContainerStatistics)
    {
        this.sessionDataListener = sessionDataListener;
        this.frameDecoder = frameDecoder;
        this.frameEncoder = frameEncoder;
        this.sessionConfig = sessionConfig;
        this.bufferPool = bufferPool;
        this.application = application;
        this.pingAgent = pingAgent;
        this.sessionStatistics = sessionStatistics;
        this.sessionContainerStatistics = sessionContainerStatistics;
    }

    @Override
    public void reset()
    {
        closed = false;
        state = SessionState.UPGRADING;
        pingAgent.reset();
        id = -1;
        channel = null;
        frameDecoder.reset();
        frameEncoder.reset();
        undeliveredDisconnectReason = null;
    }

    void init(
        final long sessionId,
        final ConnectionUpgrade connectionUpgrade,
        final Consumer<ConnectionUpgrade> releaseHandler,
        final SocketChannel channel,
        final long connectedTimestampMs)
    {
        this.id = sessionId;
        sessionStatistics.reset(sessionId, state);
        this.connectedTimestampMs = connectedTimestampMs;
        sessionDataListener.init(id);
        this.connectionUpgrade = connectionUpgrade;
        this.connectionUpgradeReleaseHandler = releaseHandler;
        if (receiveBuffer != null)
        {
            bufferPool.release(receiveBuffer);
        }
        receiveBuffer = bufferPool.acquire(sessionConfig.receiveBufferSize());
        pingAgent.init(id);
        this.frameDecoder.init(sessionStatistics);
        this.frameEncoder.init(sessionStatistics, sessionId);
        this.channel = channel;
        connectionUpgrade.init(sessionId);
    }

    /**
     * Send a message to a web socket client. The message will be fragmented into web socket frames as required.
     *
     * @param contentType indicates whether the content is text or binary.
     * @param buffer      buffer containing the message content
     * @param offset      the offset in the buffer of the message content
     * @param length      the length of the message content
     * @return an indication of whether the send was successful
     */
    @Override
    public int send(final ContentType contentType, final DirectBuffer buffer, final int offset, final int length)
    {
        if (state == SessionState.CONNECTED)
        {
            return frameEncoder.encodeMessage(contentType, buffer, offset, length);
        }
        else
        {
            return SendResult.NOT_CONNECTED;
        }
    }

    @Override
    public long id()
    {
        return id;
    }

    @Override
    public int close(final DisconnectReason disconnectReason)
    {
        sessionClosing(Constants.CLOSE_REASON_NORMAL);
        return SendResult.OK;
    }

    void onCloseMessage(final short closeReason)
    {
        sessionClosing(closeReason);
    }

    int doAdminWork()
    {
        return pingAgent.doWork() + processLingeringClose();
    }

    int doSendWork() throws IOException
    {
        if (closed)
        {
            return 0;
        }
        if ((state == SessionState.CONNECTED || state == SessionState.CLOSING || state == SessionState.VALIDATING) &&
            handshakeSendBuffer != null)
        {
            if (handshakeSendBuffer.position() != 0)
            {
                handshakeSendBuffer.flip();
                final int written = channel.write(handshakeSendBuffer);
                if (isEndOfStream(written))
                {
                    close(DisconnectReason.REMOTE_DISCONNECT);
                    return 1;
                }
                sessionStatistics.bytesWritten(written);
                sessionContainerStatistics.bytesWritten(written);
                if (handshakeSendBuffer.hasRemaining())
                {
                    handshakeSendBuffer.compact();
                }
                else
                {
                    handshakeSendBuffer.clear();
                    sessionDataListener.sendDataProcessed();
                }
                return 1;
            }

            bufferPool.release(handshakeSendBuffer);
            handshakeSendBuffer = null;
            connectionUpgradeReleaseHandler.accept(connectionUpgrade);
            connectionUpgrade = null;
            Logger.log(Category.CONNECTION, "Session %d upgrade response sent%n", id);
        }
        else if (state == SessionState.CLOSING)
        {
            if (closeReason != NULL_CLOSE_REASON && SendResult.OK == frameEncoder.encodeClose(closeReason))
            {
                closeReason = NULL_CLOSE_REASON;
            }
            if (frameEncoder.sendBuffer().remaining() == 0)
            {
                sessionClosed(DisconnectReason.LIFECYCLE);
                return 1;
            }
        }
        final int sendWork = frameEncoder.doSendWork(channel);
        if (sendWork == SendResult.NOT_CONNECTED)
        {
            sessionClosed(DisconnectReason.REMOTE_DISCONNECT);
        }
        return sendWork;
    }

    int doReceiveWork() throws IOException
    {
        if (closed)
        {
            return 0;
        }
        // previous zero-length read when in poll mode
        if (receiveBuffer.limit() == 0)
        {
            receiveBuffer.clear();
        }

        if (!pingAgent.connectionIsAlive())
        {
            sessionClosed(DisconnectReason.HEARTBEAT_TIMEOUT);
            return 1;
        }
        final int receiveBufferRemainingCapacity = receiveBuffer.remaining();
        if (receiveBufferRemainingCapacity < receiveBuffer.capacity() / 4)
        {
            allocateLargerReceiveBuffer();
        }
        final int bytesRead = channel.read(receiveBuffer);
        sessionStatistics.bytesRead(bytesRead);
        sessionContainerStatistics.bytesRead(bytesRead);
        if (isEndOfStream(bytesRead))
        {
            sessionClosed(DisconnectReason.REMOTE_DISCONNECT);
            return 1;
        }

        receiveBuffer.flip();
        sessionStatistics.receiveBufferedBytes(receiveBuffer.remaining());
        if (receiveBuffer.remaining() != 0 || frameDecoder.hasQueuedMessage())
        {
            sessionDataListener.receiveDataAvailable();
            switch (state)
            {
                case CONNECTED:
                    processReceivedMessages();
                    break;
                case UPGRADING:
                    progressUpgrade();
                    sessionStatistics.receiveBufferedBytes(receiveBuffer.remaining());
                    break;
                case VALIDATING:
                    receiveBuffer.compact();
                    break;
                case CLOSING:
                case DISCONNECTED:
                    break;
            }

            return 1;
        }
        return 0;
    }

    void validated()
    {
        setState(SessionState.CONNECTED);
        Logger.log(Category.CONNECTION, "Session %d connected%n", id);
    }

    int receiveBufferCapacity()
    {
        return receiveBuffer.capacity();
    }

    SocketChannel channel()
    {
        return channel;
    }

    long connectedTimestampMs()
    {
        return connectedTimestampMs;
    }

    private void processReceivedMessages()
    {
        for (int i = 0; i < MAX_FRAMES; i++)
        {
            final int decodeResult = frameDecoder.decode(receiveBuffer, this);
            if (decodeResult > 0)
            {
                pingAgent.messageReceived();
                receiveBuffer.position(receiveBuffer.position() + decodeResult);
                if (receiveBuffer.remaining() == 0)
                {
                    receiveBuffer.clear();
                    sessionDataListener.receiveDataProcessed();
                    sessionStatistics.receiveBufferedBytes(0);
                    break;
                }
                sessionStatistics.receiveBufferedBytes(receiveBuffer.remaining());
            }
            else if (decodeResult == SendResult.BACK_PRESSURE)
            {
                pingAgent.messageReceived();
                final int limit = receiveBuffer.limit();
                receiveBuffer.limit(receiveBuffer.capacity()).position(limit);
                sessionStatistics.receiveBufferedBytes(receiveBuffer.remaining());
                return;
            }
            else if (decodeResult == SendResult.NEED_MORE_DATA)
            {
                sessionDataListener.receiveDataProcessed();
                receiveBuffer.compact();
                sessionStatistics.receiveBufferedBytes(receiveBuffer.remaining());
                return;
            }
            else if (decodeResult == SendResult.INVALID_MESSAGE)
            {
                sessionStatistics.invalidMessageReceived();
                onCloseMessage(Constants.CLOSE_REASON_INVALID_DATA_SIZE);
                receiveBuffer.clear();
                sessionStatistics.receiveBufferedBytes(0);
                break;
            }
            if (frameDecoder.hasQueuedMessage())
            {
                // keep this session in the receive loop
                sessionDataListener.receiveDataAvailable();
                break;
            }
            else if (receiveBuffer.remaining() == 0 && receiveBuffer.position() != 0)
            {
                // nothing left to do until more data arrives
                sessionDataListener.receiveDataProcessed();
                receiveBuffer.compact();
                sessionStatistics.receiveBufferedBytes(receiveBuffer.remaining());
                break;
            }
        }
        if (receiveBuffer.remaining() != receiveBuffer.capacity())
        {
            receiveBuffer.compact();
            sessionStatistics.receiveBufferedBytes(receiveBuffer.remaining());
        }
    }

    private void progressUpgrade()
    {
        receiveBuffer.mark();
        if (handshakeSendBuffer == null)
        {
            this.handshakeSendBuffer = bufferPool.acquire(UPGRADE_SEND_BUFFER_SIZE);
        }
        Logger.log(Category.CONNECTION, "Session %d attempting upgrade%n", id);
        if (connectionUpgrade.handleUpgrade(receiveBuffer, handshakeSendBuffer))
        {
            Logger.log(Category.CONNECTION, "Session %d upgraded%n", id);
            setState(SessionState.VALIDATING);

            if (receiveBuffer.remaining() == 0)
            {
                receiveBuffer.clear();
                sessionDataListener.receiveDataProcessed();
                sessionStatistics.receiveBufferedBytes(0);
            }
            else
            {
                receiveBuffer.compact();
                sessionStatistics.receiveBufferedBytes(receiveBuffer.remaining());
            }
            sessionDataListener.sendDataAvailable();
        }
        else
        {
            receiveBuffer.reset();
        }
    }

    private void setState(final SessionState newState)
    {
        Logger.log(Category.CONNECTION, "Session %d state -> %s%n", id, newState.name());
        state = newState;
    }

    private void allocateLargerReceiveBuffer()
    {
        if (receiveBuffer.capacity() < sessionConfig.maxBufferSize())
        {
            receiveBuffer = increaseCapacity(receiveBuffer, receiveBuffer.capacity() * 2, bufferPool);
        }
    }

    private void sessionClosed(final DisconnectReason reason)
    {
        final int sendResult = application.onSessionDisconnected(this, reason);
        if (SendResult.BACK_PRESSURE == sendResult)
        {
            undeliveredDisconnectReason = reason;
            return;
        }
        undeliveredDisconnectReason = null;
        onSessionClosed();
        CloseHelper.quietClose(channel);
    }

    private int processLingeringClose()
    {
        if (undeliveredDisconnectReason != null)
        {
            sessionClosed(undeliveredDisconnectReason);
            return 1;
        }
        return 0;
    }

    private void onSessionClosed()
    {
        sessionDataListener.sessionClosed();
        closed = true;
        setState(SessionState.DISCONNECTED);
    }

    private void sessionClosing(final short closeReason)
    {
        setState(SessionState.CLOSING);
        this.closeReason = closeReason;
        // trigger call to doSendWork()
        sessionDataListener.sendDataAvailable();
    }

    private static boolean isEndOfStream(final int byteCount)
    {
        return -1 == byteCount;
    }

    /**
     * Models the current state of a web socket session.
     */
    public enum SessionState
    {
        UPGRADING,
        VALIDATING,
        CONNECTED,
        CLOSING,
        DISCONNECTED
    }
}
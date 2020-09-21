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
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.function.Consumer;

import com.aitusoftware.babl.config.SessionConfig;
import com.aitusoftware.babl.log.Category;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.monitoring.SessionContainerStatistics;
import com.aitusoftware.babl.monitoring.SessionStatistics;
import com.aitusoftware.babl.pool.BufferPool;
import com.aitusoftware.babl.pool.Pooled;
import com.aitusoftware.babl.user.Application;
import com.aitusoftware.babl.user.ContentType;

import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.EpochClock;

/**
 * Models a web socket session; used by an {@code Application} to send messages to a peer.
 */
public final class WebSocketSession implements Pooled, Session
{
    private static final int NULL_SESSION_ID = -1;
    private static final int MAX_FRAMES = 4;
    private static final int UPGRADE_SEND_BUFFER_SIZE = 1024;
    private static final short NULL_CLOSE_REASON = Short.MIN_VALUE;
    private static final long CLOSING_TIMEOUT_MS = 5_000L;

    private final FrameDecoder frameDecoder;
    private final FrameEncoder frameEncoder;
    private final SessionConfig sessionConfig;
    private final SessionDataListener sessionDataListener;
    private final BufferPool bufferPool;
    private final Application application;
    private final PingAgent pingAgent;
    private final SessionContainerStatistics sessionContainerStatistics;
    private final EpochClock epochClock;

    private long id;
    private WritableByteChannel outputChannel;
    private ReadableByteChannel inputChannel;
    private SessionStatistics sessionStatistics;
    private transient ByteBuffer handshakeSendBuffer;
    private ByteBuffer receiveBuffer;
    private SessionState state = SessionState.UPGRADING;
    private boolean closed = true;
    private short closeReason = NULL_CLOSE_REASON;
    private ConnectionUpgrade connectionUpgrade;
    private Consumer<ConnectionUpgrade> connectionUpgradeReleaseHandler;
    private DisconnectReason undeliveredDisconnectReason;
    private long connectedTimestampMs;
    private SelectionKey selectionKey;
    private long closingTimestampMs;

    WebSocketSession(
        final SessionDataListener sessionDataListener,
        final FrameDecoder frameDecoder,
        final FrameEncoder frameEncoder,
        final SessionConfig sessionConfig,
        final BufferPool bufferPool,
        final Application application,
        final PingAgent pingAgent,
        final EpochClock epochClock,
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
        this.epochClock = epochClock;
        this.sessionStatistics = sessionStatistics;
        this.sessionContainerStatistics = sessionContainerStatistics;
    }

    @Override
    public void reset()
    {
        state = SessionState.UPGRADING;
        pingAgent.reset();
        id = NULL_SESSION_ID;
        CloseHelper.close(outputChannel);
        CloseHelper.close(inputChannel);
        outputChannel = null;
        inputChannel = null;
        frameDecoder.reset();
        frameEncoder.reset();
        undeliveredDisconnectReason = null;
        selectionKey = null;
        closingTimestampMs = 0L;
    }

    void init(
        final long sessionId,
        final ConnectionUpgrade connectionUpgrade,
        final Consumer<ConnectionUpgrade> releaseHandler,
        final long connectedTimestampMs,
        final ReadableByteChannel inputChannel,
        final WritableByteChannel outputChannel)
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
        this.frameDecoder.init(sessionStatistics, sessionId);
        this.frameEncoder.init(sessionStatistics, sessionId);
        this.inputChannel = inputChannel;
        this.outputChannel = outputChannel;
        connectionUpgrade.init(sessionId);
        closed = false;
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

    /**
     * Indicates whether the session is closed.
     * @return whether the session is closed
     */
    public boolean isClosed()
    {
        return closed;
    }

    @Override
    public String toString()
    {
        return "WebSocketSession{sessionId: %d}";
    }

    void onCloseMessage(final short closeReason)
    {
        sessionClosing(closeReason);
    }

    int doAdminWork()
    {
        int workDone = 0;
        switch (state)
        {
            case CONNECTED:
                workDone = pingAgent.doWork() + processLingeringClose();
                break;
            default:
                // no-op
        }
        return workDone;
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
                final int written = outputChannel.write(handshakeSendBuffer);
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
                    if (state != SessionState.CLOSING)
                    {
                        sessionDataListener.sendDataProcessed();
                    }
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
            if (frameEncoder.sendBuffer().remaining() == 0 || closingPhaseTimeOut())
            {
                sessionClosed(DisconnectReason.LIFECYCLE);
                return 1;
            }
        }
        final int sendWork = frameEncoder.doSendWork(outputChannel);
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
        try
        {
            final int bytesRead = inputChannel.read(receiveBuffer);
            sessionStatistics.bytesRead(bytesRead);
            sessionContainerStatistics.bytesRead(bytesRead);
            if (isEndOfStream(bytesRead))
            {
                sessionClosed(DisconnectReason.REMOTE_DISCONNECT);
                return 1;
            }
        }
        catch (final IOException e)
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

    long connectedTimestampMs()
    {
        return connectedTimestampMs;
    }

    public void selectionKey(final SelectionKey selectionKey)
    {
        this.selectionKey = selectionKey;
    }

    private boolean closingPhaseTimeOut()
    {
        return epochClock.time() - closingTimestampMs >= CLOSING_TIMEOUT_MS;
    }

    private void processReceivedMessages()
    {
        for (int i = 0; i < MAX_FRAMES; i++)
        {
            final int decodeResult = frameDecoder.decode(receiveBuffer, this);
            if (decodeResult > 0)
            {
                pingAgent.messageReceived();
                sessionStatistics.receiveBufferedBytes(receiveBuffer.remaining());
                receiveBuffer.position(receiveBuffer.position() + decodeResult);
                if (receiveBuffer.remaining() == 0)
                {
                    receiveBuffer.clear();
                    sessionDataListener.receiveDataProcessed();
                    sessionStatistics.receiveBufferedBytes(0);
                    break;
                }
            }
            else if (decodeResult == SendResult.BACK_PRESSURE)
            {
                rewindReceiveBuffer();
                return;
            }
            else if (decodeResult == SendResult.NEED_MORE_DATA)
            {
                compactReceiveBuffer();
                return;
            }
            else if (decodeResult == SendResult.INVALID_MESSAGE)
            {
                closeSession();
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
                compactReceiveBuffer();
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
        Logger.log(Category.CONNECTION, "Session %d attempting upgrade with %d available bytes%n",
            id, receiveBuffer.limit());
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

    void onSessionClosed()
    {
        sessionDataListener.sessionClosed();
        selectionKey.cancel();
        closed = true;
        setState(SessionState.DISCONNECTED);
        CloseHelper.quietClose(outputChannel);
        CloseHelper.quietClose(inputChannel);
    }

    private void sessionClosing(final short closeReason)
    {
        setState(SessionState.CLOSING);
        this.closeReason = closeReason;
        this.closingTimestampMs = epochClock.time();
        // trigger call to doSendWork()
        sessionDataListener.sendDataAvailable();
    }

    private void closeSession()
    {
        sessionStatistics.invalidMessageReceived();
        onCloseMessage(Constants.CLOSE_REASON_INVALID_DATA_SIZE);
        receiveBuffer.clear();
        sessionStatistics.receiveBufferedBytes(0);
    }

    private void compactReceiveBuffer()
    {
        sessionDataListener.receiveDataProcessed();
        receiveBuffer.compact();
        sessionStatistics.receiveBufferedBytes(receiveBuffer.remaining());
    }

    private void rewindReceiveBuffer()
    {
        pingAgent.messageReceived();
        final int limit = receiveBuffer.limit();
        receiveBuffer.limit(receiveBuffer.capacity()).position(limit);
        sessionStatistics.receiveBufferedBytes(receiveBuffer.remaining());
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
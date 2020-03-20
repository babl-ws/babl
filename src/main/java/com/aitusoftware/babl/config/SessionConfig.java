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
package com.aitusoftware.babl.config;

import static io.aeron.driver.Configuration.TERM_BUFFER_IPC_LENGTH_DEFAULT;

import java.util.concurrent.TimeUnit;

import org.agrona.BitUtil;
import org.agrona.SystemUtil;

/**
 * Configuration for web-socket sessions.
 */
public final class SessionConfig
{
    private int receiveBufferSize = Integer.getInteger(
        Constants.RECEIVE_BUFFER_SIZE_PROPERTY, Constants.RECEIVE_BUFFER_SIZE_DEFAULT);
    private int sendBufferSize = Integer.getInteger(
        Constants.SEND_BUFFER_SIZE_PROPERTY, Constants.SEND_BUFFER_SIZE_DEFAULT);
    private int maxBufferSize = Integer.getInteger(
        Constants.MAX_BUFFER_SIZE_PROPERTY, Constants.MAX_BUFFER_SIZE_DEFAULT);
    private int sessionDecodeBufferSize = Integer.getInteger(
        Constants.DECODE_BUFFER_SIZE_PROPERTY, Constants.DECODE_BUFFER_SIZE_DEFAULT);
    private int maxSessionDecodeBufferSize = Integer.getInteger(
        Constants.MAX_DECODE_BUFFER_SIZE_PROPERTY, Constants.MAX_DECODE_BUFFER_SIZE_DEFAULT);
    private int maxWebSocketFrameLength = Integer.getInteger(
        Constants.MAX_WEBSOCKET_FRAME_SIZE_PROPERTY, Constants.MAX_WEBSOCKET_FRAME_SIZE_DEFAULT);
    private long pingIntervalNanos = SystemUtil.getDurationInNanos(
        Constants.PING_INTERVAL_PROPERTY, Constants.PING_INTERVAL_DEFAULT);
    private long pongResponseTimeoutNanos = SystemUtil.getDurationInNanos(
        Constants.PONG_RESPONSE_TIMEOUT_PROPERTY, Constants.PONG_RESPONSE_TIMEOUT_DEFAULT);

    /**
     * Sets the max web-socket frame length.
     *
     * @param maxWebSocketFrameLength the max frame length
     * @return this for a fluent API
     */
    public SessionConfig maxWebSocketFrameLength(final int maxWebSocketFrameLength)
    {
        this.maxWebSocketFrameLength = maxWebSocketFrameLength;
        return this;
    }

    /**
     * Returns the max web-socket frame length.
     * @return the max frame length
     */
    public int maxWebSocketFrameLength()
    {
        return this.maxWebSocketFrameLength;
    }

    /**
     * Sets the ping interval in nanoseconds.
     *
     * @param pingIntervalNanos the ping interval
     * @return this for a fluent API
     */
    public SessionConfig pingIntervalNanos(final long pingIntervalNanos)
    {
        this.pingIntervalNanos = pingIntervalNanos;
        return this;
    }

    /**
     * Returns the ping interval in nanoseconds.
     * @return the ping interval
     */
    public long pingIntervalNanos()
    {
        return this.pingIntervalNanos;
    }

    /**
     * Sets the pong response timeout in nanoseconds.
     *
     * @param pingTimeoutNanos the pong response timeout
     * @return this for a fluent API
     */
    public SessionConfig pongResponseTimeoutNanos(final long pingTimeoutNanos)
    {
        this.pongResponseTimeoutNanos = pingTimeoutNanos;
        return this;
    }

    /**
     * Returns the pong response timeout in nanoseconds.
     * @return the pong response timeout
     */
    public long pongResponseTimeoutNanos()
    {
        return this.pongResponseTimeoutNanos;
    }

    /**
     * Sets the initial session decode buffer size in bytes.
     *
     * @param sessionDecodeBufferSize the buffer size
     * @return this for a fluent API
     */
    public SessionConfig sessionDecodeBufferSize(final int sessionDecodeBufferSize)
    {
        this.sessionDecodeBufferSize = sessionDecodeBufferSize;
        return this;
    }

    /**
     * Returns the initial session decode buffer size in bytes.
     * @return the initial session decode buffer size
     */
    public int sessionDecodeBufferSize()
    {
        return this.sessionDecodeBufferSize;
    }

    /**
     * Sets the max session decode buffer size in bytes.
     *
     * @param maxSessionDecodeBufferSize the buffer size
     * @return this for a fluent API
     */
    public SessionConfig maxSessionDecodeBufferSize(final int maxSessionDecodeBufferSize)
    {
        this.maxSessionDecodeBufferSize = maxSessionDecodeBufferSize;
        return this;
    }

    /**
     * Returns the max session decode buffer size in bytes.
     * @return the max session decode buffer size
     */
    public int maxSessionDecodeBufferSize()
    {
        return this.maxSessionDecodeBufferSize;
    }

    /**
     * Sets the max buffer size in bytes.
     *
     * @param maxBufferSize the max buffer size
     * @return this for a fluent API
     */
    public SessionConfig maxBufferSize(final int maxBufferSize)
    {
        this.maxBufferSize = maxBufferSize;
        return this;
    }

    /**
     * Returns the max buffer size in bytes.
     * @return the max buffer size
     */
    public int maxBufferSize()
    {
        return BitUtil.findNextPositivePowerOfTwo(maxBufferSize);
    }

    /**
     * Sets the initial receive buffer size in bytes.
     *
     * @param receiveBufferSize the buffer size
     * @return this for a fluent API
     */
    public SessionConfig receiveBufferSize(final int receiveBufferSize)
    {
        this.receiveBufferSize = receiveBufferSize;
        return this;
    }

    /**
     * Returns the initial receive buffer size in bytes.
     * @return the buffer size
     */
    public int receiveBufferSize()
    {
        return BitUtil.findNextPositivePowerOfTwo(receiveBufferSize);
    }

    /**
     * Sets the initial send buffer size in bytes.
     *
     * @param sendBufferSize the buffer size
     * @return this for a fluent API
     */
    public SessionConfig sendBufferSize(final int sendBufferSize)
    {
        this.sendBufferSize = sendBufferSize;
        return this;
    }

    /**
     * Returns the initial send buffer size in bytes.
     * @return the buffer size
     */
    public int sendBufferSize()
    {
        return BitUtil.findNextPositivePowerOfTwo(sendBufferSize);
    }

    void conclude()
    {
        if (maxSessionDecodeBufferSize() > Constants.MAX_COMPLETE_PAYLOAD_SIZE)
        {
            throw new IllegalStateException(
                "maxSessionDecodeBuffer size must be less than " + Constants.MAX_COMPLETE_PAYLOAD_SIZE);
        }
    }

    /**
     * Constants used in configuration.
     */
    public static final class Constants
    {
        /**
         * Max complete receivable message size
         */
        public static final int MAX_COMPLETE_PAYLOAD_SIZE = TERM_BUFFER_IPC_LENGTH_DEFAULT / 8;

        /**
         * System property that will be used to set the initial receive buffer size
         */
        public static final String RECEIVE_BUFFER_SIZE_PROPERTY = "babl.session.buffer.receive.size";

        /**
         * Default value for the initial receive buffer size
         */
        public static final int RECEIVE_BUFFER_SIZE_DEFAULT = 1024;

        /**
         * System property that will be used to set the initial send buffer size
         */
        public static final String SEND_BUFFER_SIZE_PROPERTY = "babl.session.buffer.send.size";

        /**
         * Default value for the initial send buffer size
         */
        public static final int SEND_BUFFER_SIZE_DEFAULT = 1024;

        /**
         * System property that will be used to set the max buffer size
         */
        public static final String MAX_BUFFER_SIZE_PROPERTY = "babl.session.buffer.max.size";

        /**
         * Default value for the max buffer size
         */
        public static final int MAX_BUFFER_SIZE_DEFAULT = 1024 * 1024 * 16;

        /**
         * System property that will be used to set the max web-socket frame size
         */
        public static final String MAX_WEBSOCKET_FRAME_SIZE_PROPERTY = "babl.session.frame.max.size";

        /**
         * Default value for the max web-socket frame size
         */
        public static final int MAX_WEBSOCKET_FRAME_SIZE_DEFAULT = 65536;

        /**
         * System property that will be used to set the initial decode buffer size
         */
        public static final String DECODE_BUFFER_SIZE_PROPERTY =
            "babl.session.buffer.decode.size";

        /**
         * Default value for the initial decode buffer size
         */
        public static final int DECODE_BUFFER_SIZE_DEFAULT = 1024;

        /**
         * System property that will be used to set the max decode buffer size
         */
        public static final String MAX_DECODE_BUFFER_SIZE_PROPERTY =
            "babl.session.buffer.decode.max.size";

        /**
         * Default value for the max decode buffer size
         */
        public static final int MAX_DECODE_BUFFER_SIZE_DEFAULT = 65536 * 2;

        /**
         * System property that will be used to set the ping interval on the session
         */
        public static final String PING_INTERVAL_PROPERTY = "babl.session.ping.interval";

        /**
         * Default value for the ping interval on the session
         */
        public static final long PING_INTERVAL_DEFAULT = TimeUnit.SECONDS.toNanos(5L);

        /**
         * System property that will be used to set the pong response timeout on the session
         */
        public static final String PONG_RESPONSE_TIMEOUT_PROPERTY = "babl.session.pong.response.timeout";

        /**
         * Default value for the pong response timeout on the session
         */
        public static final long PONG_RESPONSE_TIMEOUT_DEFAULT = TimeUnit.SECONDS.toNanos(30L);
    }
}
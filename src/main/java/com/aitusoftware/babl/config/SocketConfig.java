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

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;

/**
 * Configuration for network sockets handled by the web-socket server.
 */
public final class SocketConfig
{
    private int receiveBufferSize = Integer.getInteger(
        Constants.RECEIVE_BUFFER_SIZE_PROPERTY, Constants.RECEIVE_BUFFER_SIZE_DEFAULT);
    private int sendBufferSize = Integer.getInteger(
        Constants.SEND_BUFFER_SIZE_PROPERTY, Constants.SEND_BUFFER_SIZE_DEFAULT);
    private boolean tcpNoDelay = ConfigUtil.mapBoolean(
        Constants.TCP_NO_DELAY_PROPERTY, Constants.TCP_NO_DELAY_DEFAULT);

    /**
     * Applies configuration to the specified channel.
     *
     * @param channel the channel to be configured
     * @throws IOException if an exception occurs when configuring the channel
     */
    public void configureChannel(final SocketChannel channel) throws IOException
    {
        channel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize());
        channel.setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize());
        channel.setOption(StandardSocketOptions.TCP_NODELAY, tcpNoDelay());
    }

    /**
     * Configures the operating system receive buffer size.
     * @param receiveBufferSize the size in bytes of the buffer
     * @return this for a fluent API
     */
    public SocketConfig receiveBufferSize(final int receiveBufferSize)
    {
        this.receiveBufferSize = receiveBufferSize;
        return this;
    }

    /**
     * Retrieves the operating system receive buffer size.
     * @return the size in bytes of the buffer
     */
    public int receiveBufferSize()
    {
        return receiveBufferSize;
    }

    /**
     * Configures the operating system send buffer size.
     * @param sendBufferSize the size in bytes of the buffer
     * @return this for a fluent API
     */
    public SocketConfig sendBufferSize(final int sendBufferSize)
    {
        this.sendBufferSize = sendBufferSize;
        return this;
    }

    /**
     * Retrieves the operating system send buffer size.
     * @return the size in bytes of the buffer
     */
    public int sendBufferSize()
    {
        return sendBufferSize;
    }

    /**
     * Configures whether Nagle's Algorithm should be disabled, by setting the {@code TCP_NO_DELAY} socket option.
     * @param tcpNoDelay indicates whether Nagle's Algorithm should be disabled
     * @return this for a fluent API
     */
    public SocketConfig tcpNoDelay(final boolean tcpNoDelay)
    {
        this.tcpNoDelay = tcpNoDelay;
        return this;
    }

    /**
     * Retrieves the setting indicating whether Nagle's Algorithm should be disabled
     * @return whether Nagle's Algorithm should be disabled
     */
    public boolean tcpNoDelay()
    {
        return tcpNoDelay;
    }

    /**
     * Constants used in configuration.
     */
    public static final class Constants
    {
        /**
         * System property that will be used to set the receive buffer size
         */
        public static final String RECEIVE_BUFFER_SIZE_PROPERTY = "babl.socket.receive.buffer.size";

        /**
         * Default value for the receive buffer size
         */
        public static final int RECEIVE_BUFFER_SIZE_DEFAULT = 65536;

        /**
         * System property that will be used to set the send buffer size
         */
        public static final String SEND_BUFFER_SIZE_PROPERTY = "babl.socket.send.buffer.size";

        /**
         * Default value for the receive buffer size
         */
        public static final int SEND_BUFFER_SIZE_DEFAULT = 65536;

        /**
         * System property that will be used to set the tcp no-delay property
         */
        public static final String TCP_NO_DELAY_PROPERTY = "babl.socket.tcpNoDelay.enabled";

        /**
         * Default value for tcp no-delay property
         */
        public static final boolean TCP_NO_DELAY_DEFAULT = false;
    }
}
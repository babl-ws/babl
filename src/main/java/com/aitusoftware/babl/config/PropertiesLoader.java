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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import com.aitusoftware.babl.config.SessionContainerConfig.Constants;

import org.agrona.SystemUtil;

/**
 * Utility class to load configuration from a properties file.
 */
public final class PropertiesLoader
{
    /**
     * Load properties from a configuration file.
     *
     * @param propertyFile the path to a {@code properties} file
     * @return the aggregate config
     */
    public static AllConfig configure(
        final Path propertyFile)
    {
        final AllConfig allConfig = new AllConfig();
        load(propertyFile, allConfig.applicationConfig(), allConfig.sessionContainerConfig(),
            allConfig.sessionConfig(), allConfig.socketConfig(), allConfig.proxyConfig());
        return allConfig;
    }

    private static void load(
        final Path propertyFile,
        final ApplicationConfig applicationConfig,
        final SessionContainerConfig sessionContainerConfig,
        final SessionConfig sessionConfig,
        final SocketConfig socketConfig,
        final ProxyConfig proxyConfig)
    {
        try (InputStream inputStream = findConfigFile(propertyFile))
        {
            final Properties properties = new Properties();
            properties.load(inputStream);
            load(properties, applicationConfig, sessionContainerConfig, sessionConfig, socketConfig, proxyConfig);
        }
        catch (final IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream findConfigFile(final Path propertyFile) throws FileNotFoundException
    {
        if (Files.exists(propertyFile))
        {
            return new FileInputStream(propertyFile.toFile());
        }
        return Optional.ofNullable(Thread.currentThread().getContextClassLoader().getResourceAsStream(
            propertyFile.toString())).orElseThrow(
                () -> new IllegalArgumentException(String.format("Cannot load resource: %s", propertyFile)));
    }

    private static void load(
        final Properties properties,
        final ApplicationConfig applicationConfig,
        final SessionContainerConfig sessionContainerConfig,
        final SessionConfig sessionConfig,
        final SocketConfig socketConfig,
        final ProxyConfig proxyConfig)
    {
        map(Constants.BIND_ADDRESS_PROPERTY, sessionContainerConfig::bindAddress, properties);
        map(Constants.SERVER_DIRECTORY_PROPERTY, sessionContainerConfig::serverDirectory, properties);
        mapInt(Constants.LISTEN_PORT_PROPERTY, sessionContainerConfig::listenPort, properties);
        mapInt(Constants.POLL_MODE_SESSION_LIMIT_PROPERTY, sessionContainerConfig::pollModeSessionLimit, properties);
        mapInt(Constants.CONNECTION_BACKLOG_PROPERTY, sessionContainerConfig::connectionBacklog, properties);
        mapInt(Constants.SESSION_CONTAINER_INSTANCE_COUNT_PROPERTY,
            sessionContainerConfig::sessionContainerInstanceCount, properties);
        mapInt(Constants.SESSION_MONITORING_FILE_ENTRY_COUNT_PROPERTY,
            sessionContainerConfig::sessionMonitoringFileEntryCount, properties);
        mapType(Constants.POLL_MODE_ENABLED_PROPERTY,
            sessionContainerConfig::pollModeEnabled, Boolean::parseBoolean, properties);

        mapInt(SessionConfig.Constants.MAX_BUFFER_SIZE_PROPERTY, sessionConfig::maxBufferSize, properties);
        mapInt(SessionConfig.Constants.RECEIVE_BUFFER_SIZE_PROPERTY, sessionConfig::receiveBufferSize, properties);
        mapInt(SessionConfig.Constants.SEND_BUFFER_SIZE_PROPERTY, sessionConfig::sendBufferSize, properties);
        mapInt(SessionConfig.Constants.DECODE_BUFFER_SIZE_PROPERTY, sessionConfig::sessionDecodeBufferSize, properties);
        mapInt(SessionConfig.Constants.MAX_DECODE_BUFFER_SIZE_PROPERTY,
            sessionConfig::maxSessionDecodeBufferSize, properties);
        mapInt(SessionConfig.Constants.MAX_WEBSOCKET_FRAME_SIZE_PROPERTY,
            sessionConfig::maxWebSocketFrameLength, properties);
        mapType(SessionConfig.Constants.PING_INTERVAL_PROPERTY, sessionConfig::pingIntervalNanos,
            value -> SystemUtil.parseDuration(SessionConfig.Constants.PING_INTERVAL_PROPERTY, value), properties);
        mapType(SessionConfig.Constants.PONG_RESPONSE_TIMEOUT_PROPERTY, sessionConfig::pongResponseTimeoutNanos,
            value -> SystemUtil.parseDuration(SessionConfig.Constants.PONG_RESPONSE_TIMEOUT_PROPERTY, value),
            properties);

        mapInt(SocketConfig.Constants.RECEIVE_BUFFER_SIZE_PROPERTY, socketConfig::receiveBufferSize, properties);
        mapInt(SocketConfig.Constants.SEND_BUFFER_SIZE_PROPERTY, socketConfig::sendBufferSize, properties);
        mapType(SocketConfig.Constants.TCP_NO_DELAY_PROPERTY,
            socketConfig::tcpNoDelay, Boolean::parseBoolean, properties);

        map(ApplicationConfig.Constants.APPLICATION_CLASS_NAME_PROPERTY,
            applicationConfig::applicationClassName, properties);

        mapInt(ProxyConfig.Constants.APPLICATION_STREAM_BASE_ID_PROPERTY,
            proxyConfig::applicationStreamBaseId, properties);
        mapInt(ProxyConfig.Constants.SERVER_STREAM_BASE_ID_PROPERTY,
            proxyConfig::serverStreamBaseId, properties);
        mapType(ProxyConfig.Constants.LAUNCH_MEDIA_DRIVER_PROPERTY,
            proxyConfig::launchMediaDriver, Boolean::parseBoolean, properties);
        mapType(ProxyConfig.Constants.PERFORMANCE_MODE_PROPERTY,
            proxyConfig::performanceMode, PerformanceMode::valueOf, properties);
        mapType(ProxyConfig.Constants.BACK_PRESSURE_POLICY_PROPERTY,
            proxyConfig::backPressurePolicy, BackPressurePolicy::valueOf, properties);
        mapInt(ProxyConfig.Constants.APPLICATION_ADAPTER_POLL_FRAGMENT_LIMIT_PROPERTY,
            proxyConfig::applicationAdapterPollFragmentLimit, properties);
        mapInt(ProxyConfig.Constants.SERVER_ADAPTER_POLL_FRAGMENT_LIMIT_PROPERTY,
            proxyConfig::serverAdapterPollFragmentLimit, properties);
    }

    private static void map(
        final String propertyName,
        final Consumer<String> receiver,
        final Properties properties)
    {
        final String propertyValue = properties.getProperty(propertyName);
        if (propertyValue != null)
        {
            receiver.accept(propertyValue);
        }
    }

    private static void mapInt(
        final String propertyName,
        final IntConsumer receiver,
        final Properties properties)
    {
        final String propertyValue = properties.getProperty(propertyName);
        if (propertyValue != null)
        {
            receiver.accept(Integer.parseInt(propertyValue));
        }
    }

    private static <T> void mapType(
        final String propertyName,
        final Consumer<T> receiver,
        final Function<String, T> mapper,
        final Properties properties)
    {
        final String propertyValue = properties.getProperty(propertyName);
        if (propertyValue != null)
        {
            receiver.accept(mapper.apply(propertyValue));
        }
    }
}
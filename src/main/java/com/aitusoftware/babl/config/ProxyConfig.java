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

import java.nio.file.Paths;

import com.aitusoftware.babl.proxy.ContextConfiguration;

import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentInvoker;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;

/**
 * Configuration for IPC proxies.
 */
public final class ProxyConfig implements AutoCloseable
{
    private final PerformanceConfig performanceConfig;

    private String mediaDriverDir = System.getProperty(
        Constants.MEDIA_DRIVER_DIRECTORY_PROPERTY, Constants.MEDIA_DRIVER_DIRECTORY_DEFAULT);
    private int applicationStreamBaseId = Integer.getInteger(
        Constants.APPLICATION_STREAM_BASE_ID_PROPERTY, Constants.APPLICATION_STREAM_BASE_ID_DEFAULT);
    private int serverStreamBaseId = Integer.getInteger(
        Constants.SERVER_STREAM_BASE_ID_PROPERTY, Constants.SERVER_STREAM_BASE_ID_DEFAULT);
    private boolean launchMediaDriver = ConfigUtil.mapBoolean(
        Constants.LAUNCH_MEDIA_DRIVER_PROPERTY, Constants.LAUNCH_MEDIA_DRIVER_DEFAULT);
    private int applicationAdapterPollFragmentLimit = Integer.getInteger(
        Constants.APPLICATION_ADAPTER_POLL_FRAGMENT_LIMIT_PROPERTY,
        Constants.APPLICATION_ADAPTER_POLL_FRAGMENT_LIMIT_DEFAULT);
    private int serverAdapterPollFragmentLimit = Integer.getInteger(
        Constants.SERVER_ADAPTER_POLL_FRAGMENT_LIMIT_PROPERTY,
        Constants.SERVER_ADAPTER_POLL_FRAGMENT_LIMIT_DEFAULT);
    private BackPressurePolicy backPressurePolicy = ConfigUtil.mapEnum(
        BackPressurePolicy::valueOf, Constants.BACK_PRESSURE_POLICY_PROPERTY, Constants.BACK_PRESSURE_POLICY_DEFAULT);

    private MediaDriver.Context mediaDriverContext = new MediaDriver.Context();
    private MediaDriver mediaDriver;
    private AgentInvoker mediaDriverInvoker;
    private Aeron.Context aeronClientContext = new Aeron.Context();
    private Aeron aeron;

    ProxyConfig(final PerformanceConfig performanceConfig)
    {
        this.performanceConfig = performanceConfig;
    }

    /**
     * Returns the Aeron client instance.
     * @return the Aeron client instance
     */
    public Aeron aeron()
    {
        return aeron;
    }

    /**
     * Returns the back-pressure policy.
     * @return the back-pressure policy
     */
    public BackPressurePolicy backPressurePolicy()
    {
        return backPressurePolicy;
    }

    /**
     * Sets the back-pressure policy.
     * @param backPressurePolicy the back-pressure policy
     * @return this for a fluent API
     */
    public ProxyConfig backPressurePolicy(final BackPressurePolicy backPressurePolicy)
    {
        this.backPressurePolicy = backPressurePolicy;
        return this;
    }

    /**
     * Returns the Aeron MediaDriver directory.
     * @return the Aeron MediaDriver directory
     */
    public String mediaDriverDir()
    {
        return mediaDriverDir;
    }

    /**
     * Sets the Aeron MediaDriver directory.
     * @param mediaDriverDir the directory
     * @return this for a fluent API
     */
    public ProxyConfig mediaDriverDir(final String mediaDriverDir)
    {
        this.mediaDriverDir = mediaDriverDir;
        return this;
    }

    /**
     * Returns the application IPC stream ID.
     * @return the application IPC stream ID
     */
    public int applicationStreamBaseId()
    {
        return applicationStreamBaseId;
    }

    /**
     * Sets the application IPC stream ID.
     * @param applicationStreamBaseId the stream ID
     * @return this for a fluent API
     */
    public ProxyConfig applicationStreamBaseId(final int applicationStreamBaseId)
    {
        this.applicationStreamBaseId = applicationStreamBaseId;
        return this;
    }

    /**
     * Returns the session container IPC stream ID.
     * @return the session container IPC stream ID
     */
    public int serverStreamBaseId()
    {
        return serverStreamBaseId;
    }

    /**
     * Sets the session container IPC stream ID.
     * @param serverStreamBaseId the stream ID
     * @return this for a fluent API
     */
    public ProxyConfig serverStreamBaseId(final int serverStreamBaseId)
    {
        this.serverStreamBaseId = serverStreamBaseId;
        return this;
    }

    /**
     * Returns whether to launch an Aeron MediaDriver.
     * @return whether to launch an Aeron MediaDriver
     */
    public boolean launchMediaDriver()
    {
        return launchMediaDriver;
    }

    /**
     * Sets whether to launch an Aeron MediaDriver.
     * @param launchMediaDriver whether to launch an Aeron MediaDriver
     * @return this for a fluent API
     */
    public ProxyConfig launchMediaDriver(final boolean launchMediaDriver)
    {
        this.launchMediaDriver = launchMediaDriver;
        return this;
    }

    /**
     * Returns the poll limit for the application IPC consumer.
     * @return the poll limit for the application IPC consumer
     */
    public int applicationAdapterPollFragmentLimit()
    {
        return applicationAdapterPollFragmentLimit;
    }

    /**
     * Sets the poll limit for the application IPC consumer.
     * @param applicationAdapterPollFragmentLimit the poll limit
     * @return this for a fluent API
     */
    public ProxyConfig applicationAdapterPollFragmentLimit(final int applicationAdapterPollFragmentLimit)
    {
        this.applicationAdapterPollFragmentLimit = applicationAdapterPollFragmentLimit;
        return this;
    }

    /**
     * Returns the poll limit for the session container IPC consumer.
     * @return the poll limit for the session container IPC consumer
     */
    public int serverAdapterPollFragmentLimit()
    {
        return serverAdapterPollFragmentLimit;
    }

    /**
     * Sets the poll limit for the session container IPC consumer.
     * @param serverAdapterPollFragmentLimit the poll limit
     * @return this for a fluent API
     */
    public ProxyConfig serverAdapterPollFragmentLimit(final int serverAdapterPollFragmentLimit)
    {
        this.serverAdapterPollFragmentLimit = serverAdapterPollFragmentLimit;
        return this;
    }

    /**
     * Returns the Aeron MediaDriver configuration context.
     * @return the Aeron MediaDriver configuration context
     */
    public MediaDriver.Context mediaDriverContext()
    {
        return mediaDriverContext;
    }

    /**
     * Returns the Aeron Client configuration context.
     * @return the Aeron Client configuration context
     */
    public Aeron.Context aeronClientContext()
    {
        return aeronClientContext;
    }

    /**
     * Returns the Aeron MediaDriver AgentInvoker.
     * @return the Aeron MediaDriver AgentInvoker.
     */
    public AgentInvoker mediaDriverInvoker()
    {
        return mediaDriverInvoker;
    }

    void conclude()
    {
        mediaDriverContext.aeronDirectoryName(mediaDriverDir);
        aeronClientContext.aeronDirectoryName(mediaDriverDir);
        ContextConfiguration.applySettings(performanceConfig.performanceMode(), mediaDriverContext);
        ContextConfiguration.applySettings(performanceConfig.performanceMode(), aeronClientContext);
        mediaDriverContext.threadingMode(ThreadingMode.INVOKER);
        if (launchMediaDriver)
        {
            mediaDriver = MediaDriver.launch(mediaDriverContext);
            mediaDriverInvoker = mediaDriver.sharedAgentInvoker();
            aeronClientContext.driverAgentInvoker(mediaDriverInvoker);
        }
        aeron = Aeron.connect(aeronClientContext);
    }

    /**
     * {@inheritDoc}
     */
    public void close()
    {
        CloseHelper.close(aeron);
        CloseHelper.close(mediaDriver);
    }

    /**
     * Constants used in configuration.
     */
    public static final class Constants
    {
        /**
         * System property that will be used to set the back-pressure policy
         */
        public static final String BACK_PRESSURE_POLICY_PROPERTY = "babl.proxy.back.pressure.policy";

        /**
         * Default value for the back-pressure policy
         */
        public static final BackPressurePolicy BACK_PRESSURE_POLICY_DEFAULT = BackPressurePolicy.CLOSE_SESSION;

        /**
         * System property that will be used to set the Aeron MediaDriver directory
         */
        public static final String MEDIA_DRIVER_DIRECTORY_PROPERTY = "babl.proxy.driver.dir";

        /**
         * Default value for the Aeron MediaDriver directory
         */
        public static final String MEDIA_DRIVER_DIRECTORY_DEFAULT = Paths.get(System.getProperty("java.io.tmpdir"))
            .resolve("proxy-driver").toString();

        /**
         * System property that will be used to set the application IPC stream ID
         */
        public static final String APPLICATION_STREAM_BASE_ID_PROPERTY = "babl.proxy.application.stream.base.id";

        /**
         * Default value for the application IPC stream ID
         */
        public static final int APPLICATION_STREAM_BASE_ID_DEFAULT = 5000;

        /**
         * System property that will be used to set the session container IPC stream ID
         */
        public static final String SERVER_STREAM_BASE_ID_PROPERTY = "babl.proxy.server.stream.base.id";

        /**
         * Default value for the session container IPC stream ID
         */
        public static final int SERVER_STREAM_BASE_ID_DEFAULT = 6000;

        /**
         * System property that will be used to set whether to launch an Aeron MediaDriver
         */
        public static final String LAUNCH_MEDIA_DRIVER_PROPERTY = "babl.proxy.driver.launch";

        /**
         * Default value for whether to launch an Aeron MediaDriver
         */
        public static final boolean LAUNCH_MEDIA_DRIVER_DEFAULT = true;

        /**
         * System property that will be used to set the poll limit for the application IPC consumer
         */
        public static final String APPLICATION_ADAPTER_POLL_FRAGMENT_LIMIT_PROPERTY =
            "babl.proxy.application.adapter.poll.limit";

        /**
         * Default value for the poll limit for the application IPC consumer
         */
        public static final int APPLICATION_ADAPTER_POLL_FRAGMENT_LIMIT_DEFAULT = 200;

        /**
         * System property that will be used to set the poll limit for the session container IPC consumer
         */
        public static final String SERVER_ADAPTER_POLL_FRAGMENT_LIMIT_PROPERTY =
            "babl.proxy.server.adapter.poll.limit";

        /**
         * Default value for the poll limit for the session container IPC consumer
         */
        public static final int SERVER_ADAPTER_POLL_FRAGMENT_LIMIT_DEFAULT = 150;
    }
}

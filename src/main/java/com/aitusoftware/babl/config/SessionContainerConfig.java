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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import com.aitusoftware.babl.pool.BufferPoolPreAllocator;
import com.aitusoftware.babl.pool.NoOpBufferPoolPreAllocator;
import com.aitusoftware.babl.websocket.AlwaysValidConnectionValidator;
import com.aitusoftware.babl.websocket.ConnectionValidator;
import com.aitusoftware.babl.websocket.routing.ConnectionRouter;
import com.aitusoftware.babl.websocket.routing.RoundRobinConnectionRouter;

import org.agrona.SystemUtil;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

/**
 * Configuration for the web socket session container.
 */
public final class SessionContainerConfig
{
    private final PerformanceConfig performanceConfig;
    private boolean pollModeEnabled = ConfigUtil.mapBoolean(
        Constants.POLL_MODE_ENABLED_PROPERTY, Constants.POLL_MODE_ENABLED_DEFAULT);
    private int pollModeSessionLimit = Integer.getInteger(
        Constants.POLL_MODE_SESSION_LIMIT_PROPERTY, Constants.POLL_MODE_SESSION_LIMIT_DEFAULT);
    private int listenPort = Integer.getInteger(
        Constants.LISTEN_PORT_PROPERTY, Constants.LISTEN_PORT_DEFAULT);
    private int connectionBacklog = Integer.getInteger(
        Constants.CONNECTION_BACKLOG_PROPERTY, Constants.CONNECTION_BACKLOG_DEFAULT);
    private String bindAddress = System.getProperty(Constants.BIND_ADDRESS_PROPERTY, Constants.BIND_ADDRESS_DEFAULT);

    private Supplier<IdleStrategy> serverIdleStrategySupplier;
    private Supplier<IdleStrategy> connectorIdleStrategySupplier =
        () -> new SleepingMillisIdleStrategy(1L);

    private ThreadFactory threadFactory = Thread::new;
    private BufferPoolPreAllocator bufferPoolPreAllocator = new NoOpBufferPoolPreAllocator();
    private String serverDirectory = System.getProperty(
        Constants.SERVER_DIRECTORY_PROPERTY, Constants.SERVER_DIRECTORY_DEFAULT);
    private int sessionMonitoringFileEntryCount = Integer.getInteger(
        Constants.SESSION_MONITORING_FILE_ENTRY_COUNT_PROPERTY, Constants.SESSION_MONITORING_FILE_ENTRY_COUNT_DEFAULT);
    private DeploymentMode deploymentMode = ConfigUtil.mapEnum(
        DeploymentMode::valueOf, Constants.DEPLOYMENT_MODE_PROPERTY, Constants.DEPLOYMENT_MODE_DEFAULT);
    private int sessionContainerInstanceCount = Integer.getInteger(
        Constants.SESSION_CONTAINER_INSTANCE_COUNT_PROPERTY, Constants.SESSION_CONTAINER_INSTANCE_COUNT_DEFAULT);
    private int sessionPollLimit = Integer.getInteger(
        Constants.SESSION_POLL_LIMIT_PROPERTY, Constants.SESSION_POLL_LIMIT_DEFAULT);
    private ConnectionValidator connectionValidator = ConfigUtil.mapInstance(ConnectionValidator.class,
        Constants.CONNECTION_VALIDATOR_PROPERTY, new AlwaysValidConnectionValidator());
    private long validationTimeoutNanos = SystemUtil.getDurationInNanos(
        SessionContainerConfig.Constants.VALIDATION_TIMEOUT_PROPERTY,
        SessionContainerConfig.Constants.VALIDATION_TIMEOUT_DEFAULT);
    private boolean autoScale = ConfigUtil.mapBoolean(Constants.AUTO_SCALE_PROPERTY,
        Constants.AUTO_SCALE_DEFAULT);
    private int activeSessionLimit = Integer.getInteger(Constants.ACTIVE_SESSION_LIMIT_PROPERTY,
        Constants.ACTIVE_SESSION_LIMIT_DEFAULT);
    private BiFunction<Path, IdleStrategy, IdleStrategy> serverIdleStrategyFactory;
    private ConnectionRouter connectionRouter;

    SessionContainerConfig(final PerformanceConfig performanceConfig)
    {
        this.performanceConfig = performanceConfig;
    }


    /**
     * Validates configuration and creates the server's mark file.
     */
    @SuppressWarnings("unchecked")
    public void conclude()
    {
        if (deploymentMode == DeploymentMode.DIRECT &&
            sessionContainerInstanceCount != Constants.SESSION_CONTAINER_INSTANCE_COUNT_DEFAULT)
        {
            throw new IllegalStateException(
                String.format("Multiple server instances are not supported in %s deployment mode.", deploymentMode));
        }
        if (sessionContainerInstanceCount != Constants.SESSION_CONTAINER_INSTANCE_COUNT_DEFAULT &&
            autoScale)
        {
            throw new IllegalStateException("Cannot use auto-scaling and specify server instance count");
        }
        if (autoScale)
        {
            sessionContainerInstanceCount =
                SessionContainerInstanceCountCalculator.calculateSessionContainerCount(this);
        }
        if (serverIdleStrategySupplier == null)
        {
            serverIdleStrategySupplier =
                () -> ConfigUtil.mapIdleStrategy(Constants.IDLE_STRATEGY_PROPERTY, performanceConfig.performanceMode());
        }
        if (System.getProperty(Constants.IDLE_STRATEGY_FACTORY_PROPERTY) != null)
        {
            serverIdleStrategyFactory =
                (BiFunction<Path, IdleStrategy, IdleStrategy>)ConfigUtil.instantiate(BiFunction.class)
                    .apply(System.getProperty(Constants.IDLE_STRATEGY_FACTORY_PROPERTY));
        }
        else
        {
            serverIdleStrategyFactory = (path, configured) -> configured;
        }
        if (System.getProperty(Constants.CONNECTION_ROUTER_FACTORY_PROPERTY) != null)
        {
            final Function<SessionContainerConfig, ConnectionRouter> connectionRouterFactory =
                (Function<SessionContainerConfig, ConnectionRouter>)ConfigUtil.instantiate(Function.class)
                .apply(System.getProperty(Constants.CONNECTION_ROUTER_FACTORY_PROPERTY));
            if (connectionRouter == null)
            {
                connectionRouter = connectionRouterFactory.apply(this);
            }
        }
        else
        {
            if (connectionRouter == null)
            {
                connectionRouter = new RoundRobinConnectionRouter(sessionContainerInstanceCount);
            }
        }
    }

    /**
     * Returns any user-configured pre-allocator for byte buffers.
     * @return the pre-allocator
     */
    public BufferPoolPreAllocator bufferPoolPreAllocator()
    {
        return bufferPoolPreAllocator;
    }

    /**
     * Sets the pre-allocator used for byte buffers on start-up.
     * @param bufferPoolPreAllocator the pre-allocator
     * @return this for a fluent API
     */
    public SessionContainerConfig bufferPoolPreAllocator(final BufferPoolPreAllocator bufferPoolPreAllocator)
    {
        this.bufferPoolPreAllocator = bufferPoolPreAllocator;
        return this;
    }

    /**
     * Indicates whether poll mode is enabled when the session count is below the configured limit.
     * @return whether poll mode is enabled
     */
    public boolean pollModeEnabled()
    {
        return pollModeEnabled;
    }

    /**
     * Sets whether poll mode is enabled when the session count is below the configured limit.
     *
     * @param pollModeEnabled whether poll mode is enabled
     * @return this for a fluent API
     */
    public SessionContainerConfig pollModeEnabled(final boolean pollModeEnabled)
    {
        this.pollModeEnabled = pollModeEnabled;
        return this;
    }

    /**
     * Returns the number of sessions that will be processed using a busy-poll, rather than using {@code epoll}.
     * @return the session count limit
     */
    public int pollModeSessionLimit()
    {
        return pollModeSessionLimit;
    }

    /**
     * Sets the number of sessions that will be processed using a busy-poll, rather than using {@code epoll}.
     * @param pollModeSessionLimit the session count limit
     * @return this for a fluent API
     */
    public SessionContainerConfig pollModeSessionLimit(final int pollModeSessionLimit)
    {
        this.pollModeSessionLimit = pollModeSessionLimit;
        return this;
    }

    /**
     * Returns the port that the server will listen on for incoming connections.
     * @return the port number
     */
    public int listenPort()
    {
        return listenPort;
    }

    /**
     * Sets the port that the server will listen on.
     * @param listenPort the port number
     * @return this for a fluent API
     */
    public SessionContainerConfig listenPort(final int listenPort)
    {
        this.listenPort = listenPort;
        return this;
    }

    /**
     * Returns the address that the server will listen on.
     * @return the address
     */
    public String bindAddress()
    {
        return bindAddress;
    }

    /**
     * Sets the address that the server will listen on.
     * @param bindAddress the address
     * @return this for a fluent API
     */
    public SessionContainerConfig bindAddress(final String bindAddress)
    {
        this.bindAddress = bindAddress;
        return this;
    }

    /**
     * Returns the connection backlog that will be maintained by the operating system.
     * @return the connection backlog
     */
    public int connectionBacklog()
    {
        return connectionBacklog;
    }

    /**
     * Sets the connection backlog that will be maintained by the operating system.
     *
     * @param connectionBacklog the connection backlog
     * @return this for a fluent API
     */
    public SessionContainerConfig connectionBacklog(final int connectionBacklog)
    {
        this.connectionBacklog = connectionBacklog;
        return this;
    }

    /**
     * Returns the {@code Supplier} of the {@code IdleStrategy} used in the server event-loop.
     * @return the idle-strategy supplier
     */
    public Supplier<IdleStrategy> serverIdleStrategySupplier()
    {
        return serverIdleStrategySupplier;
    }

    /**
     * Sets the {@code Supplier} of the {@code IdleStrategy} used in the server event-loop.
     * @param serverIdleStrategySupplier the idle-strategy supplier
     * @return this for a fluent API
     */
    public SessionContainerConfig serverIdleStrategySupplier(final Supplier<IdleStrategy> serverIdleStrategySupplier)
    {
        this.serverIdleStrategySupplier = serverIdleStrategySupplier;
        return this;
    }

    /**
     * Returns an idle strategy for a given session container instance.
     *
     * @param serverId session container instance id
     * @return the idle strategy
     */
    public IdleStrategy serverIdleStrategy(final int serverId)
    {
        return serverIdleStrategyFactory.apply(Paths.get(serverDirectory(serverId)), serverIdleStrategySupplier.get());
    }

    /**
     * Returns the {@code Supplier} of the {@code IdleStrategy} used in the connector event-loop.
     * @return the idle-strategy supplier
     */
    public Supplier<IdleStrategy> connectorIdleStrategySupplier()
    {
        return connectorIdleStrategySupplier;
    }

    /**
     * Sets the {@code Supplier} of the {@code IdleStrategy} used in the connector event-loop.
     * @param connectorIdleStrategySupplier the idle-strategy supplier
     * @return this for a fluent API
     */
    public SessionContainerConfig connectorIdleStrategySupplier(
        final Supplier<IdleStrategy> connectorIdleStrategySupplier)
    {
        this.connectorIdleStrategySupplier = connectorIdleStrategySupplier;
        return this;
    }

    /**
     * Returns the {@code ThreadFactory} used for creating event-loop threads.
     * @return the thread-factory
     */
    public ThreadFactory threadFactory()
    {
        return threadFactory;
    }

    /**
     * Sets the {@code ThreadFactory} used for creating event-loop threads.
     * @param threadFactory the thread-factory
     * @return this for a fluent API
     */
    public SessionContainerConfig threadFactory(final ThreadFactory threadFactory)
    {
        this.threadFactory = threadFactory;
        return this;
    }

    /**
     * Returns the directory used by the server for data.
     * @param serverId the ID of the server
     * @return the server directory
     */
    public String serverDirectory(final int serverId)
    {
        return sessionContainerInstanceCount == Constants.SESSION_CONTAINER_INSTANCE_COUNT_DEFAULT ?
            Paths.get(serverDirectory).toString() :
            Paths.get(serverDirectory).resolve(Integer.toString(serverId)).toString();
    }

    /**
     * Sets the directory used by the server for data.
     * @param serverDirectory the server directory
     * @return this for a fluent API
     */
    public SessionContainerConfig serverDirectory(final String serverDirectory)
    {
        this.serverDirectory = serverDirectory;
        return this;
    }

    /**
     * Returns the total number of sessions monitoring in a single monitoring file.
     * @return the number of sessions per monitoring file
     */
    public int sessionMonitoringFileEntryCount()
    {
        return sessionMonitoringFileEntryCount;
    }

    /**
     * Sets the total number of sessions monitoring in a single monitoring file.
     * @param sessionMonitoringFileEntryCount the number of sessions
     * @return this for a fluent API
     */
    public SessionContainerConfig sessionMonitoringFileEntryCount(final int sessionMonitoringFileEntryCount)
    {
        this.sessionMonitoringFileEntryCount = sessionMonitoringFileEntryCount;
        return this;
    }

    /**
     * Returns the deployment mode of the server.
     * @return the deployment mode
     */
    public DeploymentMode deploymentMode()
    {
        return this.deploymentMode;
    }

    /**
     * Sets the deployment mode for the server.
     *
     * @param deploymentMode the deployment mode
     * @return this for a fluent API
     */
    public SessionContainerConfig deploymentMode(final DeploymentMode deploymentMode)
    {
        this.deploymentMode = deploymentMode;
        return this;
    }

    /**
     * Returns the session container instance count.
     * @return the session container instance count
     */
    public int sessionContainerInstanceCount()
    {
        return sessionContainerInstanceCount;
    }

    /**
     * Sets the session container instance count.
     *
     * @param sessionContainerInstanceCount the server instance count
     * @return this for a fluent API
     */
    public SessionContainerConfig sessionContainerInstanceCount(final int sessionContainerInstanceCount)
    {
        this.sessionContainerInstanceCount = sessionContainerInstanceCount;
        return this;
    }

    /**
     * Returns the session poll limit.
     * @return the session poll limit
     */
    public int sessionPollLimit()
    {
        return this.sessionPollLimit;
    }

    /**
     * Sets the session poll limit.
     *
     * @param sessionPollLimit the session poll limit
     * @return this for a fluent API
     */
    public SessionContainerConfig sessionPollLimit(final int sessionPollLimit)
    {
        this.sessionPollLimit = sessionPollLimit;
        return this;
    }

    /**
     * Returns the connection validator.
     * @return the connection validator
     */
    public ConnectionValidator connectionValidator()
    {
        return connectionValidator;
    }

    /**
     * Sets the connection validator.
     * @param connectionValidator the connection validator
     * @return this for a fluent API
     */
    public SessionContainerConfig connectionValidator(final ConnectionValidator connectionValidator)
    {
        this.connectionValidator = connectionValidator;
        return this;
    }

    /**
     * Returns the validation timeout in nanoseconds.
     * @return the validation timeout in nanoseconds
     */
    public long validationTimeoutNanos()
    {
        return validationTimeoutNanos;
    }

    /**
     * Sets the validation timeout in nanoseconds.
     * @param validationTimeoutNanos the validation timeout in nanoseconds
     * @return this for a fluent API
     */
    public SessionContainerConfig validationTimeoutNanos(final long validationTimeoutNanos)
    {
        this.validationTimeoutNanos = validationTimeoutNanos;
        return this;
    }

    /**
     * Returns the auto-scale value.
     * @return the auto-scale value
     */
    public boolean autoScale()
    {
        return autoScale;
    }

    /**
     * Sets the auto-scale value
     * @param autoScale the auto-scale value
     * @return this for a fluent API
     */
    public SessionContainerConfig autoScale(final boolean autoScale)
    {
        this.autoScale = autoScale;
        return this;
    }

    /**
     * Returns the active session limit.
     * @return the active session limit
     */
    public int activeSessionLimit()
    {
        return activeSessionLimit;
    }

    /**
     * Sets the active session limit
     * @param activeSessionLimit the active session limit
     * @return this for a fluent API
     */
    public SessionContainerConfig activeSessionLimit(final int activeSessionLimit)
    {
        this.activeSessionLimit = activeSessionLimit;
        return this;
    }

    /**
     * Returns the connection router.
     * @return the connection router
     */
    public ConnectionRouter connectionRouter()
    {
        return connectionRouter;
    }

    /**
     * Sets the connection router.
     * @param connectionRouter the connection router
     * @return this for a fluent API
     */
    public SessionContainerConfig connectionRouter(final ConnectionRouter connectionRouter)
    {
        this.connectionRouter = connectionRouter;
        return this;
    }

    /**
     * Constants used in configuration.
     */
    public static final class Constants
    {
        /**
         * System property used to set the poll-mode enabled property
         */
        public static final String POLL_MODE_ENABLED_PROPERTY = "babl.server.poll.mode.enabled";

        /**
         * Default value for poll-mode enabled
         */
        public static final boolean POLL_MODE_ENABLED_DEFAULT = false;

        /**
         * System property used to configure the session poll-mode limit
         */
        public static final String POLL_MODE_SESSION_LIMIT_PROPERTY = "babl.server.poll.mode.session.limit";

        /**
         * Default value for the session poll-mode limit
         */
        public static final int POLL_MODE_SESSION_LIMIT_DEFAULT = 5;

        /**
         * System property used to configure the server listen port
         */
        public static final String LISTEN_PORT_PROPERTY = "babl.server.listen.port";

        /**
         * Default value for the server listen port
         */
        public static final int LISTEN_PORT_DEFAULT = 8080;

        /**
         * System property used to configure the connection backlog
         */
        public static final String CONNECTION_BACKLOG_PROPERTY = "babl.server.connection.backlog";

        /**
         * Default value for the connection backlog
         */
        public static final int CONNECTION_BACKLOG_DEFAULT = 20;

        /**
         * System property used to configure the bind address
         */
        public static final String BIND_ADDRESS_PROPERTY = "babl.server.bind.address";

        /**
         * Default value for the bind address
         */
        public static final String BIND_ADDRESS_DEFAULT = "0.0.0.0";

        /**
         * System property used to configure the server directory
         */
        public static final String SERVER_DIRECTORY_PROPERTY = "babl.server.directory";

        /**
         * Default value for the server directory
         */
        public static final String SERVER_DIRECTORY_DEFAULT = Paths.get(System.getProperty("java.io.tmpdir"))
            .resolve("babl-server").toString();

        /**
         * System property used to configure the session monitoring file entry count
         */
        public static final String SESSION_MONITORING_FILE_ENTRY_COUNT_PROPERTY =
            "babl.server.session.monitoring.entry.count";
        /**
         * Default value for the session monitoring file entry count
         */
        public static final int SESSION_MONITORING_FILE_ENTRY_COUNT_DEFAULT = 4096;

        /**
         * System property used to configure the deployment mode
         */
        public static final String DEPLOYMENT_MODE_PROPERTY = "babl.server.deployment.mode";

        /**
         * Default value for the deployment mode
         */
        public static final DeploymentMode DEPLOYMENT_MODE_DEFAULT = DeploymentMode.DIRECT;

        /**
         * System property used to configure the number of server instances
         */
        public static final String SESSION_CONTAINER_INSTANCE_COUNT_PROPERTY = "babl.server.instances";

        /**
         * Default value for the number of server instances
         */
        public static final int SESSION_CONTAINER_INSTANCE_COUNT_DEFAULT = 1;

        /**
         * System property used to configure the session poll limit
         */
        public static final String SESSION_POLL_LIMIT_PROPERTY = "babl.server.session.poll.limit";

        /**
         * Default value for the session poll limit
         */
        public static final int SESSION_POLL_LIMIT_DEFAULT = 200;

        /**
         * System property used to configure the validation timeout
         */
        public static final String VALIDATION_TIMEOUT_PROPERTY = "babl.server.validation.timeout";

        /**
         * Default value for the validation timeout
         */
        public static final long VALIDATION_TIMEOUT_DEFAULT = TimeUnit.SECONDS.toNanos(10L);

        /**
         * System property used to instantiate a connection validator
         */
        public static final String CONNECTION_VALIDATOR_PROPERTY = "babl.server.validation.validator";

        /**
         * Default value for connection validator
         */
        public static final String CONNECTION_VALIDATOR_DEFAULT = AlwaysValidConnectionValidator.class.getName();

        /**
         * System property used to configure the server event-loop idle strategy
         */
        public static final String IDLE_STRATEGY_PROPERTY = "babl.server.idle.strategy";

        /**
         * Default value for server idle strategy
         */
        public static final String IDLE_STRATEGY_DEFAULT = "BUSY_SPIN";

        /**
         * System property used to configure the server scaling mode
         */
        public static final String AUTO_SCALE_PROPERTY = "babl.server.auto.scale";

        /**
         * Default value for the server scaling mode
         */
        public static final boolean AUTO_SCALE_DEFAULT = false;

        /**
         * System property used to configure an idle strategy factory
         */
        public static final String IDLE_STRATEGY_FACTORY_PROPERTY = "babl.server.idle.strategy.factory";

        /**
         * System property used to configure a connection router factory
         */
        public static final String CONNECTION_ROUTER_FACTORY_PROPERTY = "babl.server.connection.router.factory";

        /**
         * System property used to configure the active session limit
         */
        public static final String ACTIVE_SESSION_LIMIT_PROPERTY = "babl.server.active.session.limit";

        /**
         * Default value for the active session limit
         */
        public static final int ACTIVE_SESSION_LIMIT_DEFAULT = 10_000;
    }
}
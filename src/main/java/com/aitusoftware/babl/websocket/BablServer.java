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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import com.aitusoftware.babl.config.BablConfig;
import com.aitusoftware.babl.config.BackPressurePolicy;
import com.aitusoftware.babl.config.DeploymentMode;
import com.aitusoftware.babl.config.PropertiesLoader;
import com.aitusoftware.babl.config.ProxyConfig;
import com.aitusoftware.babl.config.SessionContainerConfig;
import com.aitusoftware.babl.io.ConnectionPoller;
import com.aitusoftware.babl.monitoring.MappedApplicationAdapterStatistics;
import com.aitusoftware.babl.monitoring.MappedBroadcastStatistics;
import com.aitusoftware.babl.monitoring.MappedConnectorStatistics;
import com.aitusoftware.babl.monitoring.MappedFile;
import com.aitusoftware.babl.monitoring.MappedSessionContainerAdapterStatistics;
import com.aitusoftware.babl.monitoring.ServerMarkFile;
import com.aitusoftware.babl.proxy.ApplicationAdapter;
import com.aitusoftware.babl.proxy.ApplicationProxy;
import com.aitusoftware.babl.proxy.BroadcastProxy;
import com.aitusoftware.babl.proxy.SessionContainerAdapter;
import com.aitusoftware.babl.user.Application;
import com.aitusoftware.babl.user.BroadcastSource;
import com.aitusoftware.babl.websocket.broadcast.SessionBroadcast;

import org.agrona.ErrorHandler;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentInvoker;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.OneToOneConcurrentArrayQueue;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SystemEpochClock;
import org.agrona.concurrent.errors.DistinctErrorLog;
import org.agrona.concurrent.errors.LoggingErrorHandler;

import io.aeron.Aeron;
import io.aeron.CommonContext;
import io.aeron.ExclusivePublication;
import io.aeron.Publication;
import io.aeron.Subscription;

/**
 * Main class for starting a web socket server.
 */
public final class BablServer
{
    /**
     * Configures and starts a web socket server.
     *
     * @param args path to a properties file containing configuration
     */
    public static void main(final String[] args)
    {
        final BablConfig bablConfig;
        if (args.length > 0)
        {
            bablConfig = PropertiesLoader.configure(Paths.get(args[0]));
        }
        else
        {
            bablConfig = new BablConfig();
        }
        try (SessionContainers sessionContainers = launch(bablConfig))
        {
            sessionContainers.start();
            new ShutdownSignalBarrier().await();
        }
    }

    @SuppressWarnings("unchecked")
    public static SessionContainers launch(final BablConfig bablConfig)
    {
        bablConfig.conclude();
        final SessionContainerConfig sessionContainerConfig = bablConfig.sessionContainerConfig();
        if (sessionContainerConfig.deploymentMode() == DeploymentMode.DETACHED)
        {
            final ProxyConfig proxyConfig = bablConfig.proxyConfig();
            final int applicationStreamId = proxyConfig.applicationStreamBaseId();
            final Application application = bablConfig.applicationConfig().application();
            final Aeron aeron = proxyConfig.aeron();
            final Publication toApplicationPublication =
                sessionContainerConfig.sessionContainerInstanceCount() == 1 ?
                aeron.addExclusivePublication(CommonContext.IPC_CHANNEL, applicationStreamId) :
                aeron.addPublication(CommonContext.IPC_CHANNEL, applicationStreamId);
            final Subscription toApplicationSubscription =
                aeron.addSubscription(CommonContext.IPC_CHANNEL, applicationStreamId);
            final int instanceCount = sessionContainerConfig.sessionContainerInstanceCount();
            final Publication[] toServerPublications = new Publication[instanceCount];
            final int applicationInstanceId = 0;
            final SessionContainer[] sessionContainers = new SessionContainer[instanceCount];
            final ServerMarkFile[] serverMarkFiles = new ServerMarkFile[instanceCount];
            final Queue<SocketChannel>[] toServerChannels = new Queue[instanceCount];
            final BackPressureStrategy backPressureStrategy = forPolicy(proxyConfig.backPressurePolicy());
            final List<AutoCloseable> dependencies = new ArrayList<>();
            final MappedFile applicationAdapterMappedFile =
                new MappedFile(Paths.get(sessionContainerConfig.serverDirectory(0),
                MappedApplicationAdapterStatistics.FILE_NAME), MappedApplicationAdapterStatistics.LENGTH);
            final MappedApplicationAdapterStatistics applicationAdapterStatistics =
                new MappedApplicationAdapterStatistics(applicationAdapterMappedFile);
            final boolean enableBroadcast = application instanceof BroadcastSource;
            if (enableBroadcast)
            {
                final ExclusivePublication broadcastPublication =
                    aeron.addExclusivePublication(CommonContext.IPC_CHANNEL, proxyConfig.broadcastStreamId());
                ((BroadcastSource)application).setBroadcast(
                    new BroadcastProxy(broadcastPublication, applicationAdapterStatistics));
            }

            for (int i = 0; i < instanceCount; i++)
            {
                initialiseServerInstance(
                    bablConfig, sessionContainerConfig, proxyConfig, aeron, toApplicationPublication,
                    toServerPublications, sessionContainers, serverMarkFiles, toServerChannels, backPressureStrategy,
                    dependencies, enableBroadcast, i);
            }
            final ServerMarkFile serverMarkFile = serverMarkFiles[0];
            final DistinctErrorLog errorLog = new DistinctErrorLog(serverMarkFile.errorBuffer(),
                SystemEpochClock.INSTANCE);
            final ErrorHandler errorHandler = new LoggingErrorHandler(errorLog);

            applicationAdapterStatistics.reset();
            dependencies.add(applicationAdapterStatistics);

            final int maxActiveSessionCount =
                sessionContainerConfig.sessionContainerInstanceCount() *
                sessionContainerConfig.activeSessionLimit();
            final ApplicationAdapter applicationAdapter = new ApplicationAdapter(
                applicationInstanceId, application,
                toApplicationSubscription,
                toServerPublications,
                proxyConfig.applicationAdapterPollFragmentLimit(),
                applicationAdapterStatistics,
                maxActiveSessionCount,
                SystemEpochClock.INSTANCE);
            final Agent applicationAgent = constructApplicationAgent(bablConfig, applicationAdapter);
            final AgentRunner applicationAdapterRunner = new AgentRunner(
                bablConfig.applicationConfig().applicationIdleStrategy(sessionContainerConfig.serverDirectory(0)),
                errorHandler, null,
                applicationAgent);
            AgentRunner.startOnThread(applicationAdapterRunner, sessionContainerConfig.threadFactory());
            final ServerSocketChannel serverSocketChannel = createServerSocket(sessionContainerConfig);
            final IdleStrategy connectorIdleStrategy = sessionContainerConfig.connectorIdleStrategySupplier().get();
            final MappedConnectorStatistics mappedConnectorStatistics = new MappedConnectorStatistics(
                new MappedFile(Paths.get(sessionContainerConfig.serverDirectory(0),
                MappedConnectorStatistics.FILE_NAME), MappedConnectorStatistics.LENGTH));
            dependencies.add(mappedConnectorStatistics);
            final ConnectionPoller connectionPoller = new ConnectionPoller(serverSocketChannel,
                toServerChannels, connectorIdleStrategy, bablConfig.socketConfig(),
                sessionContainerConfig.connectionRouter(), mappedConnectorStatistics);
            final AgentRunner connectorAgentRunner = new AgentRunner(connectorIdleStrategy, errorHandler,
                null, connectionPoller);
            AgentRunner.startOnThread(connectorAgentRunner, sessionContainerConfig.threadFactory());
            dependencies.add(bablConfig.proxyConfig());
            dependencies.add(applicationAdapterRunner);
            dependencies.add(connectorAgentRunner);
            return new SessionContainers(sessionContainers,
                Arrays.asList(applicationAdapterRunner, connectorAgentRunner, bablConfig.proxyConfig()));
        }
        else
        {
            return launchDirectServer(bablConfig, sessionContainerConfig);
        }
    }

    private static Agent constructApplicationAgent(
        final BablConfig config,
        final ApplicationAdapter applicationAdapter)
    {
        final AgentInvoker mediaDriverInvoker = config.proxyConfig().mediaDriverInvoker();
        final Agent additionalWork = config.applicationConfig().additionalWork();
        if (mediaDriverInvoker != null && additionalWork != null)
        {
            return new TripleAgent(applicationAdapter, mediaDriverInvoker.agent(), additionalWork);
        }
        if (mediaDriverInvoker != null)
        {
            return new DoubleAgent(applicationAdapter, mediaDriverInvoker.agent());
        }
        if (additionalWork != null)
        {
            return new DoubleAgent(applicationAdapter, additionalWork);
        }
        return applicationAdapter;
    }

    private static void initialiseServerInstance(
        final BablConfig bablConfig,
        final SessionContainerConfig sessionContainerConfig,
        final ProxyConfig proxyConfig,
        final Aeron aeron,
        final Publication toApplicationPublication,
        final Publication[] toServerPublications,
        final SessionContainer[] sessionContainers,
        final ServerMarkFile[] serverMarkFiles,
        final Queue<SocketChannel>[] toServerChannels,
        final BackPressureStrategy backPressureStrategy,
        final List<AutoCloseable> dependencies,
        final boolean enableBroadcast,
        final int sessionContainerId)
    {
        final int serverSubscriptionStreamId = proxyConfig.serverStreamBaseId() + sessionContainerId;
        final Subscription toServerSubscription =
            aeron.addSubscription(CommonContext.IPC_CHANNEL,
            serverSubscriptionStreamId);
        final Subscription broadcastSubscription = enableBroadcast ?
            aeron.addSubscription(CommonContext.IPC_CHANNEL, proxyConfig.broadcastStreamId()) : null;
        final Long2ObjectHashMap<Session> sessionByIdMap = new Long2ObjectHashMap<>();
        final ApplicationProxy applicationProxy = new ApplicationProxy(sessionContainerId, sessionByIdMap);
        toServerChannels[sessionContainerId] = new OneToOneConcurrentArrayQueue<>(16);
        final MappedFile broadcastStatsFile = new MappedFile(
            Paths.get(sessionContainerConfig.serverDirectory(sessionContainerId), MappedBroadcastStatistics.FILE_NAME),
            MappedBroadcastStatistics.LENGTH);
        final SessionContainerAdapter sessionContainerAdapter = new SessionContainerAdapter(
            sessionContainerId, sessionByIdMap, toServerSubscription, broadcastSubscription,
            proxyConfig.serverAdapterPollFragmentLimit(), backPressureStrategy,
            new SessionBroadcast(sessionByIdMap, new MappedBroadcastStatistics(broadcastStatsFile)));
        sessionContainers[sessionContainerId] = new SessionContainer(
            sessionContainerId,
            applicationProxy, bablConfig.sessionConfig(),
            sessionContainerConfig,
            sessionContainerAdapter,
            toServerChannels[sessionContainerId]);
        toServerPublications[sessionContainerId] = aeron.addExclusivePublication(CommonContext.IPC_CHANNEL,
            serverSubscriptionStreamId);
        serverMarkFiles[sessionContainerId] = sessionContainers[sessionContainerId].serverMarkFile();
        final MappedFile serverAdapterStatsFile = new MappedFile(
            Paths.get(sessionContainerConfig.serverDirectory(sessionContainerId),
            MappedSessionContainerAdapterStatistics.FILE_NAME), MappedSessionContainerAdapterStatistics.LENGTH);
        final MappedSessionContainerAdapterStatistics sessionAdapterStatistics =
            new MappedSessionContainerAdapterStatistics(serverAdapterStatsFile);
        dependencies.add(sessionAdapterStatistics);
        sessionAdapterStatistics.reset();
        applicationProxy.init(toApplicationPublication,
            sessionContainers[sessionContainerId].sessionContainerStatistics());
        sessionContainerAdapter.sessionAdapterStatistics(sessionAdapterStatistics);
    }

    @SuppressWarnings("unchecked")
    private static SessionContainers launchDirectServer(
        final BablConfig bablConfig, final SessionContainerConfig sessionContainerConfig)
    {
        final Application application = bablConfig.applicationConfig().application();
        final IdleStrategy connectorIdleStrategy = sessionContainerConfig.connectorIdleStrategySupplier().get();
        final Queue<SocketChannel> incomingConnections = new OneToOneConcurrentArrayQueue<>(16);
        final SessionContainer sessionContainer = new SessionContainer(
            application, bablConfig.sessionConfig(),
            sessionContainerConfig, incomingConnections);
        final ServerSocketChannel serverSocketChannel = createServerSocket(sessionContainerConfig);
        final DistinctErrorLog errorLog = new DistinctErrorLog(sessionContainer.serverMarkFile().errorBuffer(),
            new SystemEpochClock());
        final ErrorHandler errorHandler = new LoggingErrorHandler(errorLog);
        final MappedConnectorStatistics mappedConnectorStatistics = new MappedConnectorStatistics(
            new MappedFile(Paths.get(sessionContainerConfig.serverDirectory(0),
            MappedConnectorStatistics.FILE_NAME), MappedConnectorStatistics.LENGTH));
        final ConnectionPoller connectionPoller = new ConnectionPoller(serverSocketChannel,
            new Queue[] {incomingConnections}, connectorIdleStrategy, bablConfig.socketConfig(),
            sessionContainerConfig.connectionRouter(), mappedConnectorStatistics);
        final AgentRunner connectorAgentRunner = new AgentRunner(
            connectorIdleStrategy, errorHandler,
            null, connectionPoller);
        AgentRunner.startOnThread(connectorAgentRunner, sessionContainerConfig.threadFactory());
        return new SessionContainers(sessionContainer);
    }

    private static ServerSocketChannel createServerSocket(final SessionContainerConfig sessionContainerConfig)
    {
        final ServerSocketChannel serverSocketChannel;
        try
        {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(
                sessionContainerConfig.bindAddress(), sessionContainerConfig.listenPort()),
                sessionContainerConfig.connectionBacklog());
        }
        catch (final IOException e)
        {
            throw new UncheckedIOException(e);
        }
        return serverSocketChannel;
    }

    private static BackPressureStrategy forPolicy(final BackPressurePolicy backPressurePolicy)
    {
        final BackPressureStrategy strategy;
        switch (backPressurePolicy)
        {
            case CLOSE_SESSION:
                strategy = new DisconnectBackPressureStrategy();
                break;
            case DROP_MESSAGE:
                strategy = new DropMessageBackPressureStrategy();
                break;
            case PROPAGATE:
                strategy = new MaintainBackPressureStrategy();
                break;
            default:
                throw new IllegalArgumentException("Unknown policy: " + backPressurePolicy.name());
        }
        return strategy;
    }
}
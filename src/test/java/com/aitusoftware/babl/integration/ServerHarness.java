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
package com.aitusoftware.babl.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.aitusoftware.babl.config.AllConfig;
import com.aitusoftware.babl.config.DeploymentMode;
import com.aitusoftware.babl.config.PerformanceMode;
import com.aitusoftware.babl.config.ProxyConfig;
import com.aitusoftware.babl.config.SessionConfig;
import com.aitusoftware.babl.config.SessionContainerConfig;
import com.aitusoftware.babl.monitoring.ApplicationAdapterStatisticsPrinter;
import com.aitusoftware.babl.monitoring.ErrorPrinter;
import com.aitusoftware.babl.monitoring.MappedApplicationAdapterStatistics;
import com.aitusoftware.babl.monitoring.MappedSessionContainerAdapterStatistics;
import com.aitusoftware.babl.monitoring.SessionContainerAdapterStatisticsPrinter;
import com.aitusoftware.babl.monitoring.SessionContainerStatisticsPrinter;
import com.aitusoftware.babl.performance.PortProbe;
import com.aitusoftware.babl.user.Application;
import com.aitusoftware.babl.websocket.BablServer;
import com.aitusoftware.babl.websocket.SessionContainers;

import org.agrona.CloseHelper;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

public final class ServerHarness implements AutoCloseable
{
    private static final IdleStrategy IDLE_STRATEGY = new SleepingMillisIdleStrategy(1L);
    private final AllConfig allConfig = new AllConfig();
    private final SessionContainerConfig sessionContainerConfig = allConfig.sessionContainerConfig()
        .serverIdleStrategySupplier(() -> IDLE_STRATEGY);
    private final SessionConfig sessionConfig = allConfig.sessionConfig()
        .sendBufferSize(131072).maxSessionDecodeBufferSize(131072)
        .pingIntervalNanos(TimeUnit.SECONDS.toNanos(2L));
    private final ProxyConfig proxyConfig = allConfig.proxyConfig();
    private final Application application;

    private SessionContainers sessionContainers;
    private Path serverDir;

    public ServerHarness(final Application application)
    {
        this.application = application;
    }

    public void start(final Path serverDir) throws IOException
    {
        allConfig.applicationConfig().application(application);
        allConfig.proxyConfig().performanceMode(PerformanceMode.LOW);
        allConfig.applicationConfig().applicationIdleStrategySupplier(() -> new SleepingMillisIdleStrategy(1));
        this.serverDir = serverDir;
        sessionContainerConfig
            .listenPort(findFreePort())
            .serverDirectory(serverDir.toString())
            .sessionMonitoringFileEntryCount(32);
        sessionContainers = BablServer.launch(allConfig);
        sessionContainers.start();
        PortProbe.ensurePortOpen(sessionContainerConfig.listenPort());
    }

    private int findFreePort()
    {
        for (int i = 0; i < 100; i++)
        {
            final int port = ThreadLocalRandom.current().nextInt(1000) + 8000;
            try (SocketChannel channel = SocketChannel.open())
            {
                channel.connect(new InetSocketAddress("127.0.0.1", port));
            }
            catch (final IOException e)
            {
                return port;
            }
        }

        throw new IllegalStateException("Unable to find free port");
    }

    public SessionContainerConfig serverConfig()
    {
        return sessionContainerConfig;
    }

    public SessionConfig sessionConfig()
    {
        return sessionConfig;
    }

    public ProxyConfig proxyConfig()
    {
        return proxyConfig;
    }

    @Override
    public void close()
    {
        if (serverDir != null)
        {
            for (int i = 0; i < sessionContainerConfig.sessionContainerInstanceCount(); i++)
            {
                final String[] args = {sessionContainerConfig.serverDirectory(i)};
                ErrorPrinter.main(args);
                SessionContainerStatisticsPrinter.main(args);
                printSessionContainerAdapterStatistics(Paths.get(args[0]).resolve(
                    MappedSessionContainerAdapterStatistics.FILE_NAME));
            }
            printApplicationAdapterStatistics(
                Paths.get(sessionContainerConfig.serverDirectory(0))
                .resolve(MappedApplicationAdapterStatistics.FILE_NAME));
        }
        CloseHelper.close(sessionContainers);
    }

    public int serverPort()
    {
        return sessionContainerConfig.listenPort();
    }

    private void printApplicationAdapterStatistics(final Path adapterStatsFile)
    {
        if (sessionContainerConfig.deploymentMode() == DeploymentMode.DETACHED)
        {
            ApplicationAdapterStatisticsPrinter.main(new String[]
            {
                adapterStatsFile.toString()
            });
        }
    }

    private void printSessionContainerAdapterStatistics(final Path adapterStatsFile)
    {
        if (sessionContainerConfig.deploymentMode() == DeploymentMode.DETACHED)
        {
            SessionContainerAdapterStatisticsPrinter.main(new String[]
            {
                adapterStatsFile.toString()
            });
        }
    }
}
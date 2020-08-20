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
package com.aitusoftware.babl.ext;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.aitusoftware.babl.config.DeploymentMode;
import com.aitusoftware.babl.config.SessionContainerConfig;
import com.aitusoftware.babl.monitoring.MappedApplicationAdapterStatistics;
import com.aitusoftware.babl.monitoring.MappedFile;
import com.aitusoftware.babl.monitoring.MappedSessionContainerAdapterStatistics;
import com.aitusoftware.babl.monitoring.MappedSessionContainerStatistics;
import com.aitusoftware.babl.monitoring.ServerMarkFile;
import com.aitusoftware.babl.monitoring.SessionStatisticsFile;
import com.aitusoftware.babl.monitoring.SessionStatisticsFileReader;

import org.agrona.CloseHelper;
import org.agrona.IoUtil;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;

final class StatisticsMonitoringAgent implements Agent
{
    private static final long SESSION_FILE_CHECK_INTERVAL_NS = TimeUnit.SECONDS.toNanos(1);
    private static final int FORCE_IDLE = 0;
    private final Set<Path> sessionStatisticsFiles = new HashSet<>();
    private final List<Closeable> closeables = new ArrayList<>();
    private final SessionContainerConfig sessionContainerConfig;
    private final MonitoringConsumer monitoringConsumer;
    private MappedApplicationAdapterStatistics applicationAdapterStatistics;
    private MappedSessionContainerAdapterStatistics[] sessionContainerAdapterStatistics;
    private MappedSessionContainerStatistics[] sessionContainerStatistics;
    private MappedErrorBuffer[] errorBuffers;
    private long lastSessionFileCheckTimestamp;

    StatisticsMonitoringAgent(
        final SessionContainerConfig sessionContainerConfig,
        final MonitoringConsumer monitoringConsumer)
    {
        this.sessionContainerConfig = sessionContainerConfig;
        this.monitoringConsumer = monitoringConsumer;
    }

    @Override
    public int doWork()
    {
        final long currentTimeNs = System.nanoTime();
        if (currentTimeNs > lastSessionFileCheckTimestamp + SESSION_FILE_CHECK_INTERVAL_NS)
        {
            assignDataSources();
            lastSessionFileCheckTimestamp = currentTimeNs;
            checkForNewSessionFiles();
        }
        if (applicationAdapterStatistics != null)
        {
            monitoringConsumer.applicationAdapterStatistics(applicationAdapterStatistics);
        }
        if (sessionContainerAdapterStatistics != null)
        {
            monitoringConsumer.sessionAdapterStatistics(sessionContainerAdapterStatistics);
        }
        monitoringConsumer.sessionContainerStatistics(sessionContainerStatistics);
        monitoringConsumer.errorBuffers(errorBuffers);
        for (final Path entry : sessionStatisticsFiles)
        {
            SessionStatisticsFileReader.readEntries(entry, statistics ->
                monitoringConsumer.sessionStatistics(entry, statistics));
        }

        return FORCE_IDLE;
    }

    @Override
    public String roleName()
    {
        return "statistics-monitoring-agent";
    }

    @Override
    public void onClose()
    {
        closeables.addAll(Arrays.asList(errorBuffers));
        if (sessionContainerAdapterStatistics != null)
        {
            closeables.addAll(Arrays.asList(sessionContainerAdapterStatistics));
        }
        closeables.add(applicationAdapterStatistics);
        CloseHelper.closeAll(closeables);
    }

    private void checkForNewSessionFiles()
    {
        for (int i = 0; i < sessionContainerConfig.sessionContainerInstanceCount(); i++)
        {
            final Path containerDir = Paths.get(sessionContainerConfig.serverDirectory(i));
            try (Stream<Path> statisticFiles =
                Files.list(containerDir).filter(SessionStatisticsFile::isSessionStatisticsFile))
            {
                statisticFiles.forEach(sessionStatisticsFiles::add);
            }
            catch (final IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }
    }

    private boolean requiredPathsExist()
    {
        boolean allExist = true;
        final int instanceCount = sessionContainerConfig.sessionContainerInstanceCount();
        if (sessionContainerConfig.deploymentMode() == DeploymentMode.DETACHED)
        {
            final Path primaryServerPath = Paths.get(sessionContainerConfig.serverDirectory(0));
            allExist &= Files.exists(primaryServerPath.resolve(MappedApplicationAdapterStatistics.FILE_NAME));
            for (int i = 0; i < instanceCount; i++)
            {
                allExist &= Files.exists(Paths.get(sessionContainerConfig.serverDirectory(i))
                    .resolve(MappedSessionContainerAdapterStatistics.FILE_NAME));
            }
        }
        for (int i = 0; i < instanceCount; i++)
        {
            allExist &= Files.exists(Paths.get(
                sessionContainerConfig.serverDirectory(i)).resolve(ServerMarkFile.MARK_FILE_NAME));
        }

        return allExist;
    }

    private void assignDataSources()
    {
        if (sessionContainerStatistics == null && requiredPathsExist())
        {
            final int instanceCount = sessionContainerConfig.sessionContainerInstanceCount();
            if (sessionContainerConfig.deploymentMode() == DeploymentMode.DETACHED)
            {
                final Path primaryServerPath = Paths.get(sessionContainerConfig.serverDirectory(0));
                applicationAdapterStatistics = new MappedApplicationAdapterStatistics(
                    new MappedFile(primaryServerPath.resolve(MappedApplicationAdapterStatistics.FILE_NAME),
                        MappedApplicationAdapterStatistics.LENGTH));
                sessionContainerAdapterStatistics =
                    new MappedSessionContainerAdapterStatistics[instanceCount];
                for (int i = 0; i < instanceCount; i++)
                {
                    sessionContainerAdapterStatistics[i] = new MappedSessionContainerAdapterStatistics(
                        new MappedFile(Paths.get(sessionContainerConfig.serverDirectory(i))
                            .resolve(MappedSessionContainerAdapterStatistics.FILE_NAME),
                            MappedSessionContainerAdapterStatistics.LENGTH));
                }
            }
            else
            {
                applicationAdapterStatistics = null;
                sessionContainerAdapterStatistics = null;
            }
            sessionContainerStatistics = new MappedSessionContainerStatistics[instanceCount];
            errorBuffers = new MappedErrorBuffer[instanceCount];

            for (int i = 0; i < instanceCount; i++)
            {
                final Path markFile = Paths.get(
                    sessionContainerConfig.serverDirectory(i)).resolve(ServerMarkFile.MARK_FILE_NAME);
                final MappedByteBuffer buffer = IoUtil.mapExistingFile(
                    markFile.toFile(), "statistics-buffer",
                    ServerMarkFile.DATA_OFFSET, ServerMarkFile.DATA_LENGTH);
                closeables.add(() -> IoUtil.unmap(buffer));
                sessionContainerStatistics[i] =
                    new MappedSessionContainerStatistics(new UnsafeBuffer(buffer), 0);
                errorBuffers[i] = new MappedErrorBuffer(markFile,
                    ServerMarkFile.ERROR_BUFFER_OFFSET, ServerMarkFile.ERROR_BUFFER_LENGTH);
            }
        }
    }
}
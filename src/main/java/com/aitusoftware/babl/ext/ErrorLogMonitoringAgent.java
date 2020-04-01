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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import com.aitusoftware.babl.monitoring.ServerMarkFile;

import org.agrona.CloseHelper;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.errors.ErrorLogReader;

final class ErrorLogMonitoringAgent implements Agent
{
    private final int index;
    private final String roleName;
    private final Path sessionContainerDir;
    private MappedErrorBuffer mappedErrorBuffer;
    private long maxObservedTimestamp;

    ErrorLogMonitoringAgent(final int index, final Path sessionContainerDir)
    {
        this.index = index;
        this.sessionContainerDir = sessionContainerDir;
        roleName = "error-log-monitor-" + index;
    }

    @Override
    public int doWork()
    {
        if (mappedErrorBuffer == null)
        {
            if (Files.exists(sessionContainerDir.resolve(ServerMarkFile.MARK_FILE_NAME)))
            {
                mappedErrorBuffer = new MappedErrorBuffer(sessionContainerDir.resolve(ServerMarkFile.MARK_FILE_NAME),
                    ServerMarkFile.ERROR_BUFFER_OFFSET, ServerMarkFile.ERROR_BUFFER_LENGTH);
                return 1;
            }
        }
        else
        {
            return ErrorLogReader.read(mappedErrorBuffer.errorBuffer(), this::printException, maxObservedTimestamp);
        }

        return 0;
    }

    private void printException(
        final int observationCount,
        final long firstObservationTimestamp,
        final long lastObservationTimestamp,
        final String encodedException)
    {
        System.out.printf("Server ID %d; observations: %d, first: %s, last: %s%n",
            index,
            observationCount,
            Instant.ofEpochMilli(firstObservationTimestamp),
            Instant.ofEpochMilli(lastObservationTimestamp));
        System.out.printf("%s%n", encodedException);
        maxObservedTimestamp = Math.max(maxObservedTimestamp, lastObservationTimestamp + 1);
    }

    @Override
    public void onClose()
    {
        CloseHelper.close(mappedErrorBuffer);
    }

    @Override
    public String roleName()
    {
        return roleName;
    }
}
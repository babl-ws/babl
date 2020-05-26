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
package com.aitusoftware.babl.monitoring;

import java.nio.MappedByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import org.agrona.IoUtil;
import org.agrona.concurrent.UnsafeBuffer;

public final class SessionContainerStatisticsPrinter
{
    public static void main(final String[] args)
    {
        readServerStatistics(Paths.get(args[0]),
            (timestamp, bytesRead, bytesWritten, activeSessionCount,
            receiveBackPressureEvents, invalidOpCodeEvents,
            maxEventLoopDurationMs, proxyBackPressureEvents,
            proxyBackPressured) ->
            {
                System.out.printf("Timestamp: %s%n", Instant.ofEpochMilli(timestamp));
                System.out.printf("Bytes Read:                 %20d%n", bytesRead);
                System.out.printf("Bytes Written:              %20d%n", bytesWritten);
                System.out.printf("Active Sessions:            %20d%n", activeSessionCount);
                System.out.printf("Back Pressure Events:       %20d%n", receiveBackPressureEvents);
                System.out.printf("Invalid Opcode Events:      %20d%n", invalidOpCodeEvents);
                System.out.printf("Max Event Loop Ms    :      %20d%n", maxEventLoopDurationMs);
                System.out.printf("Proxy Back Pressure Events: %20d%n", proxyBackPressureEvents);
                System.out.printf("Proxy Back Pressured:       %20s%n", proxyBackPressured);
            });
    }

    public static void readServerStatistics(
        final Path serverDir,
        final StatisticsReceiver statisticsReceiver)
    {
        final MappedByteBuffer buffer = IoUtil.mapExistingFile(
            serverDir.resolve(ServerMarkFile.MARK_FILE_NAME).toFile(),
            "statistics-buffer",
            ServerMarkFile.DATA_OFFSET, ServerMarkFile.DATA_LENGTH);
        try
        {
            final MappedSessionContainerStatistics serverStatistics =
                new MappedSessionContainerStatistics(new UnsafeBuffer(buffer), 0);

            statisticsReceiver.serverStatistics(
                serverStatistics.timestamp(),
                serverStatistics.bytesRead(),
                serverStatistics.bytesWritten(),
                serverStatistics.activeSessionCount(),
                serverStatistics.receiveBackPressureEvents(),
                serverStatistics.invalidOpCodeEvents(),
                serverStatistics.maxEventLoopDurationMs(),
                serverStatistics.proxyBackPressureEvents(),
                serverStatistics.isProxyBackPressured());
        }
        finally
        {
            IoUtil.unmap(buffer);
        }
    }

    public interface StatisticsReceiver
    {
        void serverStatistics(
            long timestamp,
            long bytesRead,
            long bytesWritten,
            int activeSessionCount,
            long receiveBackPressureEvents,
            long invalidOpCodeEvents,
            long maxEventLoopDurationMs,
            long proxyBackPressureEvents,
            boolean proxyBackPressured);
    }
}
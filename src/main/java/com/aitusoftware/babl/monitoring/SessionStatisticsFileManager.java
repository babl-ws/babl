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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SessionStatisticsFileManager
{
    private final List<SessionStatisticsFile> statisticsFiles = new ArrayList<>();
    private final Path dir;
    private final int entryCount;

    public SessionStatisticsFileManager(
        final Path dir,
        final int initialFileCount,
        final int entryCount)
    {
        this.dir = dir;
        this.entryCount = entryCount;
        for (int i = 0; i < Math.max(initialFileCount, 1); i++)
        {
            final String filename = SessionStatisticsFile.filename(i);
            statisticsFiles.add(new SessionStatisticsFile(dir.resolve(filename), entryCount));
        }
    }

    public void assign(final MappedSessionStatistics sessionStatistics)
    {
        for (int i = 0; i < statisticsFiles.size(); i++)
        {
            final SessionStatisticsFile statisticsFile = statisticsFiles.get(i);

            if (!statisticsFile.isFull())
            {
                assignToFile(sessionStatistics, statisticsFile);
                return;
            }
        }

        final SessionStatisticsFile statisticsFile =
            new SessionStatisticsFile(dir.resolve(SessionStatisticsFile.filename(statisticsFiles.size())), entryCount);
        statisticsFiles.add(statisticsFile);
        assignToFile(sessionStatistics, statisticsFile);
    }

    private void assignToFile(
        final MappedSessionStatistics sessionStatistics,
        final SessionStatisticsFile statisticsFile)
    {
        statisticsFile.assign(sessionStatistics);
    }
}
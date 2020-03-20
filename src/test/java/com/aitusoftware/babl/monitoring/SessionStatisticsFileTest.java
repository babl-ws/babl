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

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aitusoftware.babl.websocket.WebSocketSession;

import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionStatisticsFileTest
{
    private static final long SESSION_ONE_ID = 17L;
    private static final long SESSION_TWO_ID = 37L;
    private static final long SESSION_THREE_ID = 1337L;
    private static final int ENTRY_COUNT = 8;

    @TempDir
    Path tmpDir;

    private SessionStatisticsFile statisticsFile;
    private Path filePath;

    @BeforeEach
    void setUp()
    {
        filePath = tmpDir.resolve("session-statistics.data");
        statisticsFile = new SessionStatisticsFile(filePath, ENTRY_COUNT);
    }

    @AfterEach
    void tearDown()
    {
        CloseHelper.close(statisticsFile);
    }

    @Test
    void shouldAddNewEntries()
    {
        final MappedSessionStatistics one = new MappedSessionStatistics();
        final MappedSessionStatistics two = new MappedSessionStatistics();
        final MappedSessionStatistics three = new MappedSessionStatistics();
        statisticsFile.assign(one);
        statisticsFile.assign(two);
        statisticsFile.assign(three);

        one.reset(SESSION_ONE_ID, WebSocketSession.SessionState.CONNECTED);
        one.receiveBufferedBytes(1);
        two.reset(SESSION_TWO_ID, WebSocketSession.SessionState.CONNECTED);
        two.receiveBufferedBytes(2);
        three.reset(SESSION_THREE_ID, WebSocketSession.SessionState.CONNECTED);
        three.receiveBufferedBytes(3);

        final Map<Long, Integer> capturedStats = new HashMap<>();

        SessionStatisticsFileReader.readEntries(filePath, stats ->
            capturedStats.put(stats.sessionId(), stats.receiveBufferedBytes()));

        assertThat(one.receiveBufferedBytes()).isEqualTo(1);
        assertThat(two.receiveBufferedBytes()).isEqualTo(2);
        assertThat(three.receiveBufferedBytes()).isEqualTo(3);

        assertThat(capturedStats.size()).isEqualTo(3);
        assertThat(capturedStats.get(SESSION_ONE_ID)).isEqualTo(1);
        assertThat(capturedStats.get(SESSION_TWO_ID)).isEqualTo(2);
        assertThat(capturedStats.get(SESSION_THREE_ID)).isEqualTo(3);
        assertThat(statisticsFile.size()).isEqualTo(3);
    }

    @Test
    void shouldContainMaximumEntryCount()
    {
        fill();

        assertThat(statisticsFile.assign(new MappedSessionStatistics())).isFalse();
        assertThat(statisticsFile.size()).isEqualTo(ENTRY_COUNT);
    }

    @Test
    void insertionAndRemoval()
    {
        final List<MappedSessionStatistics> assigned = fill();

        statisticsFile.remove(assigned.get(0));
        statisticsFile.remove(assigned.get(2));
        statisticsFile.remove(assigned.get(4));

        final MappedSessionStatistics firstNew = new MappedSessionStatistics();
        statisticsFile.assign(firstNew);

        statisticsFile.remove(assigned.get(6));

        final MappedSessionStatistics secondNew = new MappedSessionStatistics();
        statisticsFile.assign(secondNew);

        firstNew.receiveBufferedBytes(37);
        assertThat(firstNew.receiveBufferedBytes()).isEqualTo(37);
        secondNew.receiveBufferedBytes(23);
        assertThat(secondNew.receiveBufferedBytes()).isEqualTo(23);

        statisticsFile.remove(assigned.get(1));
        statisticsFile.remove(assigned.get(3));
        statisticsFile.remove(assigned.get(5));
        statisticsFile.remove(assigned.get(7));

        statisticsFile.assign(firstNew);
        statisticsFile.assign(secondNew);

        firstNew.receiveBufferedBytes(137);
        assertThat(firstNew.receiveBufferedBytes()).isEqualTo(137);
        secondNew.receiveBufferedBytes(123);
        assertThat(secondNew.receiveBufferedBytes()).isEqualTo(123);
    }

    @Test
    void shouldRemoveEntryAtEndOfFile()
    {
        assertRemoval(ENTRY_COUNT - 1);
    }

    @Test
    void shouldRemoveEntryAtStartOfFile()
    {
        assertRemoval(0);
    }

    @Test
    void shouldRemoveEntryInMiddleOfFile()
    {
        assertRemoval(ENTRY_COUNT / 2);
    }

    private void assertRemoval(final int index)
    {
        final List<MappedSessionStatistics> assigned = fill();

        statisticsFile.remove(assigned.get(index));

        assertThat(statisticsFile.size()).isEqualTo(ENTRY_COUNT - 1);

        final MappedSessionStatistics sessionStatistics = new MappedSessionStatistics();
        assertThat(statisticsFile.assign(sessionStatistics)).isTrue();
        final int receiveBufferedBytes = 37;
        sessionStatistics.receiveBufferedBytes(receiveBufferedBytes);
        assertThat(sessionStatistics.receiveBufferedBytes()).isEqualTo(receiveBufferedBytes);
    }

    private List<MappedSessionStatistics> fill()
    {
        final List<MappedSessionStatistics> assigned = new ArrayList<>();
        for (int i = 0; i < ENTRY_COUNT; i++)
        {
            final MappedSessionStatistics statistics = new MappedSessionStatistics();
            assigned.add(statistics);
            assertThat(statisticsFile.assign(statistics)).isTrue();
        }

        return assigned;
    }
}
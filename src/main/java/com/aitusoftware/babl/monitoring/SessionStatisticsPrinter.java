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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public final class SessionStatisticsPrinter
{
    public static void main(final String[] args) throws IOException
    {
        try (Stream<Path> files = Files.list(Paths.get(args[0]))
            .filter(SessionStatisticsFile::isSessionStatisticsFile)
            .sorted())
        {
            files.forEach(path ->
                SessionStatisticsFileReader.readEntries(path, SessionStatisticsPrinter::printSessionStatistics));
        }
    }

    private static void printSessionStatistics(final MappedSessionStatistics sessionStatistics)
    {
        System.out.printf("Session ID:                %20d%n", sessionStatistics.sessionId());
        System.out.printf("Session State:             %20s%n", sessionStatistics.sessionState());
        System.out.printf("Bytes Read:                %20d%n", sessionStatistics.bytesRead());
        System.out.printf("Bytes Written:             %20d%n", sessionStatistics.bytesWritten());
        System.out.printf("Frames Decoded:         %20d%n", sessionStatistics.framesDecoded());
        System.out.printf("Frames Encoded:         %20d%n", sessionStatistics.framesEncoded());
        System.out.printf("Messages Received:         %20d%n", sessionStatistics.messagesReceived());
        System.out.printf("Messages Sent:             %20d%n", sessionStatistics.messagesSent());
        System.out.printf("Receive Buffered Bytes:    %20d%n", sessionStatistics.receiveBufferedBytes());
        System.out.printf("Send Buffered Bytes:       %20d%n", sessionStatistics.sendBufferedBytes());
        System.out.printf("Invalid Messages Received: %20d%n", sessionStatistics.invalidMessagesReceived());
        System.out.printf("Invalid Pings Received:    %20d%n", sessionStatistics.invalidPingsReceived());
        System.out.printf("Send Back Pressure Events: %20d%n", sessionStatistics.sendBackPressureEvents());
        System.out.println();
    }
}
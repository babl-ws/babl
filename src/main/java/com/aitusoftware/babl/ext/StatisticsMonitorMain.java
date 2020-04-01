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

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import com.aitusoftware.babl.config.PropertiesLoader;
import com.aitusoftware.babl.config.SessionContainerConfig;
import com.aitusoftware.babl.monitoring.MappedApplicationAdapterStatistics;
import com.aitusoftware.babl.monitoring.MappedSessionAdapterStatistics;
import com.aitusoftware.babl.monitoring.MappedSessionContainerStatistics;
import com.aitusoftware.babl.monitoring.MappedSessionStatistics;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.errors.ErrorLogReader;

/**
 * Program to periodically report statistics.
 */
public final class StatisticsMonitorMain
{
    private static final String MONITORING_CONSUMER_CLASS_NAME_PROPERTY = "babl.monitoring.consumer.class.name";
    private static final SleepingMillisIdleStrategy IDLE_STRATEGY = new SleepingMillisIdleStrategy(1_000L);
    private static final ErrorHandler ERROR_HANDLER = Throwable::printStackTrace;

    /**
     * Main method for executing the program.
     * @param args the properties file used for configuring the server instance
     */
    public static void main(final String[] args)
    {
        final SessionContainerConfig sessionContainerConfig =
            PropertiesLoader.configure(Paths.get(args[0])).sessionContainerConfig();
        final Agent monitoringAgent = new StatisticsMonitoringAgent(sessionContainerConfig, loadMonitoringConsumer());
        try (AgentRunner agentRunner = new AgentRunner(
            IDLE_STRATEGY, ERROR_HANDLER, null, monitoringAgent))
        {
            AgentRunner.startOnThread(agentRunner);
            new ShutdownSignalBarrier().await();
        }
    }

    private static MonitoringConsumer loadMonitoringConsumer()
    {
        final String consumerClassName = System.getProperty(
            MONITORING_CONSUMER_CLASS_NAME_PROPERTY, LoggingMonitoringConsumer.class.getName());
        try
        {
            return (MonitoringConsumer)Class.forName(consumerClassName).getDeclaredConstructors()[0].newInstance();
        }
        catch (final InstantiationException | IllegalAccessException |
            InvocationTargetException | ClassNotFoundException e)
        {
            throw new IllegalArgumentException(String.format("Failed to instantiate %s",
                consumerClassName), e);
        }
    }

    private static final class LoggingMonitoringConsumer implements MonitoringConsumer
    {

        private static final NoOpErrorConsumer NO_OP_ERROR_CONSUMER = new NoOpErrorConsumer();

        @Override
        public void applicationAdapterStatistics(
            final MappedApplicationAdapterStatistics applicationAdapterStatistics)
        {
            System.out.printf("Application Adapter Statistics%n");
            System.out.printf("Proxy back-pressure events: %20d%n",
                applicationAdapterStatistics.proxyBackPressureCount());
            System.out.printf("Poll-limit reached count:   %20d%n",
                applicationAdapterStatistics.pollLimitReachedCount());
        }

        @Override
        public void sessionAdapterStatistics(
            final MappedSessionAdapterStatistics[] sessionAdapterStatistics)
        {
            for (int i = 0; i < sessionAdapterStatistics.length; i++)
            {
                final MappedSessionAdapterStatistics stats = sessionAdapterStatistics[i];
                System.out.printf("Session Adapter %d Statistics%n", i);
                System.out.printf("Proxy back-pressure events: %20d%n", stats.proxyBackPressureCount());
                System.out.printf("Poll-limit reached count:   %20d%n", stats.pollLimitReachedCount());
            }
        }

        @Override
        public void errorBuffers(
            final MappedErrorBuffer[] errorBuffers)
        {
            long totalErrorCount = 0;
            for (final MappedErrorBuffer errorBuffer : errorBuffers)
            {
                totalErrorCount += ErrorLogReader.read(errorBuffer.errorBuffer(), NO_OP_ERROR_CONSUMER);
            }
            System.out.printf("Total error count:      %20d%n", totalErrorCount);
        }

        @Override
        public void sessionContainerStatistics(
            final MappedSessionContainerStatistics[] sessionContainerStatistics)
        {
            for (int i = 0; i < sessionContainerStatistics.length; i++)
            {
                final MappedSessionContainerStatistics stats = sessionContainerStatistics[i];
                System.out.printf("Session Container %d Statistics%n", i);
                System.out.printf("Timestamp: %s%n", Instant.ofEpochMilli(stats.timestamp()));
                System.out.printf("Bytes Read:           %20d%n", stats.bytesRead());
                System.out.printf("Bytes Written:        %20d%n", stats.bytesWritten());
                System.out.printf("Active Sessions:      %20d%n", stats.activeSessionCount());
                System.out.printf("Back Pressure Events: %20d%n", stats.receiveBackPressureEvents());
                System.out.printf("Invalid Opcode Events:%20d%n", stats.invalidOpCodeEvents());
                System.out.printf("Max Event Loop Ms    :%20d%n", stats.maxEventLoopDurationMs());
            }
        }

        @Override
        public void sessionStatistics(
            final Path statisticsFile,
            final MappedSessionStatistics sessionStatistics)
        {
            System.out.printf("Session Statistics: %s%n", statisticsFile);
            System.out.printf("Session ID:                %20d%n", sessionStatistics.sessionId());
            System.out.printf("Session State:             %20s%n", sessionStatistics.sessionState());
            System.out.printf("Bytes Read:                %20d%n", sessionStatistics.bytesRead());
            System.out.printf("Bytes Written:             %20d%n", sessionStatistics.bytesWritten());
            System.out.printf("Frames Decoded:            %20d%n", sessionStatistics.framesDecoded());
            System.out.printf("Frames Encoded:            %20d%n", sessionStatistics.framesEncoded());
            System.out.printf("Messages Received:         %20d%n", sessionStatistics.messagesReceived());
            System.out.printf("Messages Sent:             %20d%n", sessionStatistics.messagesSent());
            System.out.printf("Receive Buffered Bytes:    %20d%n", sessionStatistics.receiveBufferedBytes());
            System.out.printf("Send Buffered Bytes:       %20d%n", sessionStatistics.sendBufferedBytes());
            System.out.printf("Invalid Messages Received: %20d%n", sessionStatistics.invalidMessagesReceived());
            System.out.printf("Invalid Pings Received:    %20d%n", sessionStatistics.invalidPingsReceived());
            System.out.printf("Send Back Pressure Events: %20d%n", sessionStatistics.sendBackPressureEvents());

        }
    }
}
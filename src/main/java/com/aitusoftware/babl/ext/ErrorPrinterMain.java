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

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.aitusoftware.babl.config.PropertiesLoader;
import com.aitusoftware.babl.config.SessionContainerConfig;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.CompositeAgent;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

/**
 * Program to continuously check {@code SessionContainer} error buffers for new exceptions.
 * Exceptions will be printed to stdout as they are read.
 */
public final class ErrorPrinterMain
{
    private static final SleepingMillisIdleStrategy IDLE_STRATEGY = new SleepingMillisIdleStrategy(10L);
    private static final ErrorHandler ERROR_HANDLER = Throwable::printStackTrace;

    /**
     * Main method for executing the program.
     * @param args the properties file used for configuring the server instance
     */
    public static void main(final String[] args)
    {
        final SessionContainerConfig sessionContainerConfig =
            PropertiesLoader.configure(Paths.get(args[0])).sessionContainerConfig();
        final List<Agent> agentList = new ArrayList<>();
        for (int i = 0; i < sessionContainerConfig.sessionContainerInstanceCount(); i++)
        {
            agentList.add(new ErrorLogMonitoringAgent(i, Paths.get(sessionContainerConfig.serverDirectory(i))));
        }
        final CompositeAgent monitoringAgents = new CompositeAgent(agentList);
        try (AgentRunner agentRunner = new AgentRunner(
            IDLE_STRATEGY, ERROR_HANDLER, null, monitoringAgents))
        {
            AgentRunner.startOnThread(agentRunner);
            new ShutdownSignalBarrier().await();
        }
    }
}

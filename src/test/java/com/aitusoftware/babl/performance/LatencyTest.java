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
package com.aitusoftware.babl.performance;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import com.aitusoftware.babl.integration.ServerHarness;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.performance.resin.ResinWebSocketServer;
import com.aitusoftware.babl.user.EchoApplication;
import com.aitusoftware.babl.config.DeploymentMode;
import com.aitusoftware.babl.config.PerformanceMode;
import com.aitusoftware.babl.config.ProxyConfig;

import org.agrona.CloseHelper;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.aeron.driver.MediaDriver;

@Disabled
class LatencyTest
{
    private static final int CLIENT_COUNT = 20;
    private static final int CLIENT_THREAD_COUNT = 4;
    private static final int WARMUP_ITERATIONS = 15;
    private static final int MESSAGES_PER_ITERATION = 15_000;
    private static final int MEASUREMENT_ITERATIONS = 40;
    private static final int PAYLOAD_SIZE = 60;
    private static final int MESSAGES_PER_SECOND = 20_000;

    private final ServerHarness harness = new ServerHarness(new EchoApplication(false));
    private final VertxServer vertxServer = new VertxServer(8192);
    private final ResinWebSocketServer resinServer = new ResinWebSocketServer();
    private LatencyTestHarness testHarness;
    @TempDir
    Path workingDir;

    @BeforeAll
    static void disableLogging()
    {
        System.setProperty(Logger.DEBUG_ENABLED_PROPERTY, Boolean.FALSE.toString());
    }

    @BeforeEach
    void setUp() throws Exception
    {
        harness.sessionConfig().pingIntervalNanos(TimeUnit.SECONDS.toNanos(30L));
        harness.serverConfig().pollModeEnabled(true)
            .serverIdleStrategySupplier(NoOpIdleStrategy::new)
            .serverDirectory(workingDir.resolve("server").toString());

        Files.createDirectories(workingDir.resolve("driver"));
        testHarness = new LatencyTestHarness(
            CLIENT_COUNT, CLIENT_THREAD_COUNT, MESSAGES_PER_SECOND, MEASUREMENT_ITERATIONS,
            WARMUP_ITERATIONS, MESSAGES_PER_ITERATION, PAYLOAD_SIZE);
    }

    @Test
    void testVertxLatency() throws Exception
    {
        vertxServer.start();
        testHarness.runLatencyTest(new InetSocketAddress("localhost", vertxServer.actualPort()));
    }

    @Test
    void testBablLatency() throws Exception
    {
        harness.start(workingDir);
        testHarness.runLatencyTest(new InetSocketAddress("localhost", harness.serverPort()));
    }

    @Test
    void testBablDetachedLatency() throws Exception
    {
        harness.serverConfig().deploymentMode(DeploymentMode.DETACHED);
        harness.serverConfig().sessionContainerInstanceCount(1);
        final ProxyConfig proxyConfig = harness.proxyConfig();
        final MediaDriver.Context mediaDriverContext = proxyConfig.mediaDriverContext();
        mediaDriverContext.termBufferSparseFile(false);
        proxyConfig.aeronClientContext().preTouchMappedMemory(true);
        proxyConfig.applicationAdapterPollFragmentLimit(15_000)
            .serverAdapterPollFragmentLimit(14_000);

        final Path driverDir = Paths.get("/dev/shm/babl");
        Files.createDirectories(driverDir);
        proxyConfig
            .launchMediaDriver(true)
            .mediaDriverDir(driverDir.toString());
        harness.performanceConfig().performanceMode(PerformanceMode.HIGH);
        final Path serverDir = workingDir.resolve("server");

        harness.start(serverDir);
        testHarness.runLatencyTest(new InetSocketAddress("localhost", harness.serverPort()));
    }

    @Test
    void testResinLatency() throws Exception
    {
        resinServer.start(8080);
        testHarness.runLatencyTest(new InetSocketAddress("localhost", 8080));
    }

    @AfterEach
    void tearDown()
    {
        CloseHelper.closeAll(harness, vertxServer, resinServer);
    }
}
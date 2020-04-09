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
package com.aitusoftware.babl.docker;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.websocket.Client;
import com.aitusoftware.babl.websocket.ClientEventHandler;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

@DisabledIfEnvironmentVariable(named = "CIRCLECI", matches = "true")
class DockerComposeIntegrationTest
{
    private static final Path DOCKER_DIR = Paths.get("docker");
    private static final String ERROR_MESSAGE = "send error";
    private static final String DOCKER_COMPOSE_PATH = "/usr/local/bin/docker-compose";

    private Process dockerComposeProcess;
    private Path stdOut;

    @TempDir
    Path tmpDir;


    @BeforeEach
    void setUp() throws IOException
    {
        assertFileExists(Paths.get("scripts", "docker-build.sh"));
        assertFileExists(DOCKER_DIR.resolve("docker-compose.yaml"));
        stdOut = tmpDir.resolve("stdout.txt");
        final Path stdErr = tmpDir.resolve("stderr.txt");
        dockerComposeProcess = new ProcessBuilder()
            .directory(DOCKER_DIR.toFile())
            .redirectError(stdErr.toFile())
            .redirectOutput(stdOut.toFile())
            .command(DOCKER_COMPOSE_PATH, "up")
            .start();
    }

    @Test
    void shouldExposeMonitoringData() throws IOException
    {
        waitForLineContaining("babl-stats-monitor", "Session Container \\d Statistics");
        waitForLineContaining("babl-stats-monitor", "Max Event Loop Ms    :\\s+\\d+");

        sendErrorMessage();

        waitForLineContaining("babl-error-monitor", "Boom!", "Exception");
    }

    @AfterEach
    void tearDown() throws InterruptedException, IOException
    {
        if (dockerComposeProcess != null)
        {
            dockerComposeProcess.destroy();
            if (!dockerComposeProcess.waitFor(45, TimeUnit.SECONDS))
            {
                Assertions.fail("Compose process did not exit");
            }

            new ProcessBuilder()
                .directory(DOCKER_DIR.toFile())
                .redirectOutput(stdOut.toFile())
                .command(DOCKER_COMPOSE_PATH, "down")
                .start().waitFor(30, TimeUnit.SECONDS);
        }
    }

    private void sendErrorMessage() throws IOException
    {
        final Client client = new Client(new NoOpClientEventHandler());
        client.connect(new URL("http://localhost:8080/"));
        assertThat(client.upgradeConnection()).isTrue();
        final MutableDirectBuffer msg = new UnsafeBuffer(new byte[100]);
        msg.putBytes(0, ERROR_MESSAGE.getBytes(StandardCharsets.UTF_8));
        assertThat(client.offer(msg, 0, 10, ContentType.BINARY)).isTrue();
        assertThat(client.doWork()).isGreaterThan(0);
    }

    private void waitForLineContaining(final String... patterns)
    {
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> lineFound(patterns));
    }

    private boolean lineFound(final String[] patterns)
    {
        try (Stream<String> stdOutLines = Files.lines(stdOut))
        {
            return stdOutLines.anyMatch(line ->
            {
                for (final String pattern : patterns)
                {
                    if (!Pattern.compile(pattern).matcher(line).find())
                    {
                        return false;
                    }
                }
                return true;
            });
        }
        catch (final IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private void assertFileExists(final Path path)
    {
        if (!Files.exists(path))
        {
            Assertions.fail("File not found: " + path);
        }
    }

    private static class NoOpClientEventHandler implements ClientEventHandler
    {
        @Override
        public void onMessage(
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final ContentType contentType)
        {

        }

        @Override
        public void onHeartbeatTimeout()
        {

        }

        @Override
        public void onConnectionClosed()
        {

        }
    }
}
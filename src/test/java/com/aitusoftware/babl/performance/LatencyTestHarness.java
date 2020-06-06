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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.aitusoftware.babl.user.ContentType;
import com.aitusoftware.babl.websocket.Client;
import com.aitusoftware.babl.websocket.ClientEventHandler;

import org.HdrHistogram.Histogram;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

@SuppressWarnings("ForLoopReplaceableByForEach")
final class LatencyTestHarness
{
    private static final AtomicInteger GENERATOR_ID = new AtomicInteger();

    private final int clientCount;
    private final int threadCount;
    private final int messagesPerSecond;
    private final int measurementIterations;
    private final int warmUpIterations;
    private final int messagesPerIteration;
    private final int payloadSize;
    private final Config config;

    LatencyTestHarness(
        final int clientCount, final int threadCount, final int messagesPerSecond,
        final int measurementIterations, final int warmUpIterations,
        final int messagesPerIteration, final int payloadSize)
    {
        this.clientCount = clientCount;
        this.threadCount = threadCount;
        this.messagesPerSecond = messagesPerSecond;
        this.measurementIterations = measurementIterations;
        this.warmUpIterations = warmUpIterations;
        this.messagesPerIteration = messagesPerIteration;
        this.payloadSize = payloadSize;
        config = new Config(clientCount, threadCount, messagesPerSecond);
    }

    Histogram runLatencyTest(final InetSocketAddress serverAddress)
        throws InterruptedException, IOException, TimeoutException, ExecutionException
    {
        final ExecutorService threadPool = Executors.newFixedThreadPool(threadCount);
        final long interMessageInterval = TimeUnit.SECONDS.toNanos(1) / messagesPerSecond;
        final long expectedRuntimeNs = (warmUpIterations + measurementIterations) *
            (messagesPerIteration * interMessageInterval);
        final List<List<ClientAndHandler>> clientSets =
            distributeClients(serverAddress, config);
        logConfig(messagesPerSecond, expectedRuntimeNs,
            warmUpIterations, measurementIterations, messagesPerIteration, config);
        final Histogram allResults = latencyHistogram();
        final List<Future<Histogram>> histogramResults = new ArrayList<>();
        try
        {
            for (int i = 0; i < threadCount; i++)
            {
                final List<ClientAndHandler> clientSet = clientSets.get(i);
                final Future<Histogram> measurementResult = executeLoad(
                    threadPool, warmUpIterations, messagesPerIteration, measurementIterations,
                    payloadSize, interMessageInterval, clientSet, clientCount);
                histogramResults.add(measurementResult);
            }
            for (final Future<Histogram> histogramResult : histogramResults)
            {
                final Histogram histogram = histogramResult.get(5 * expectedRuntimeNs, TimeUnit.NANOSECONDS);
                allResults.add(histogram);
            }
            if (allResults.getTotalCount() < 0.9f * measurementIterations * messagesPerIteration)
            {
                throw new IllegalStateException(String.format("Unexpected total number of results: %d",
                    allResults.getTotalCount()));
            }
        }
        finally
        {
            threadPool.shutdown();
            if (!threadPool.awaitTermination(5 * expectedRuntimeNs, TimeUnit.NANOSECONDS))
            {
                System.err.println("WARN: Threads did not stop");
            }
        }
        allResults.outputPercentileDistribution(System.out, 1d);
        return allResults;
    }

    private Future<Histogram> executeLoad(
        final ExecutorService threadPool, final int warmupIterations,
        final int messagesPerIteration, final int measurementIterations,
        final int payloadSize, final long interMessageInterval,
        final List<ClientAndHandler> clientSet,
        final int totalClientCount)
    {
        return threadPool.submit(() ->
        {
            Thread.currentThread().setName("babl-latency-test-" + GENERATOR_ID.getAndIncrement());
            try
            {
                final Histogram resultHistogram = latencyHistogram();
                final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[payloadSize]);
                deliverMessageBatches(resultHistogram, clientSet,
                    warmupIterations, messagesPerIteration,
                    payloadSize, buffer, interMessageInterval, totalClientCount);
                resultHistogram.reset();
                System.out.printf("[%s] warmup complete%n", Thread.currentThread().getName());
                System.out.flush();
                final long backPressureCount = deliverMessageBatches(resultHistogram, clientSet,
                    measurementIterations, messagesPerIteration,
                    payloadSize, buffer, interMessageInterval, totalClientCount);
                if (backPressureCount != 0)
                {
                    System.out.printf("[%s] Back-pressure count: %d%n",
                        Thread.currentThread().getName(), backPressureCount);
                }
                return resultHistogram;
            }
            catch (final Exception e)
            {
                e.printStackTrace(System.out);
                throw new RuntimeException(e);
            }
        });
    }

    private static Histogram latencyHistogram()
    {
        return new Histogram(10_000_000, 3);
    }

    private static void logConfig(
        final long messagesPerSecond,
        final long expectedRuntimeNs,
        final int warmUpIterations,
        final int measurementIterations,
        final int messagesPerIteration,
        final Config config)
    {
        System.out.printf("Using %d threads, %d clients per-thread%n", config.threadCount, config.perThreadClientCount);
        System.out.printf("%d warm-up iterations, %d measurement iterations%n",
            warmUpIterations, measurementIterations);
        System.out.printf("Aggregate throughput %d msg/sec%n", messagesPerSecond);
        System.out.printf("Per-thread batch internal %dus%n", TimeUnit.NANOSECONDS.toMicros(
            config.perThreadBatchIntervalNs));
        System.out.printf("Expected runtime %ds%n", TimeUnit.NANOSECONDS.toSeconds(expectedRuntimeNs));
        System.out.printf("Expecting ~%d total measurement results%n", measurementIterations * messagesPerIteration);
    }

    private static List<List<ClientAndHandler>> distributeClients(
        final InetSocketAddress serverAddress,
        final Config config) throws IOException
    {
        final List<List<ClientAndHandler>> clientSets = new ArrayList<>();
        for (int i = 0; i < config.threadCount; i++)
        {
            clientSets.add(new ArrayList<>());
        }
        final URL url = new URL(String.format("http://%s:%s/foo",
            serverAddress.getHostString(), serverAddress.getPort()));

        for (int i = 0; i < config.clientCount; i++)
        {
            final Histogram histogram = latencyHistogram();
            final MeasuringClientEventHandler eventHandler = new MeasuringClientEventHandler(histogram,
                config.perThreadBatchIntervalNs);
            final Client client = new Client(eventHandler);
            client.connect(url);
            if (!client.upgradeConnection())
            {
                throw new IllegalStateException("Failed to upgrade connection");
            }
            clientSets.get(i % config.threadCount).add(new ClientAndHandler(client, eventHandler));
        }
        return clientSets;
    }

    private static final class Config
    {
        private final long perThreadClientCount;
        private final long perThreadBatchIntervalNs;
        private final int threadCount;
        private final int clientCount;

        private Config(
            final int clientCount,
            final int threadCount,
            final int messagesPerSecond)
        {
            if (clientCount % threadCount != 0)
            {
                throw new IllegalArgumentException("Client count must be a multiple of thread count");
            }
            if (clientCount < threadCount)
            {
                throw new IllegalArgumentException("Client count must be >= thread count");
            }
            final long globalInterMessageIntervalNs = TimeUnit.SECONDS.toNanos(1) / messagesPerSecond;
            perThreadClientCount = clientCount / threadCount;
            perThreadBatchIntervalNs = globalInterMessageIntervalNs * clientCount;
            this.threadCount = threadCount;
            this.clientCount = clientCount;
        }
    }

    private static long deliverMessageBatches(
        final Histogram resultHistogram,
        final List<ClientAndHandler> clients,
        final int iterations, final int messagesPerIteration,
        final int payloadSize, final MutableDirectBuffer buffer,
        final long interMessageInterval,
        final int totalClientCount) throws IOException
    {
        final long interBatchInterval = interMessageInterval * totalClientCount;
        long backPressureCount = 0;
        int remainingIterations = iterations;
        while (remainingIterations-- != 0)
        {
            final int totalBatchCount = messagesPerIteration / totalClientCount;
            int remainingBatches = totalBatchCount;
            while (remainingBatches-- != 0)
            {
                final long startTime = System.nanoTime();
                for (int clientIndex = 0; clientIndex < clients.size(); clientIndex++)
                {
                    final long currentTime = System.nanoTime();
                    buffer.putLong(0, currentTime);
                    final ClientAndHandler clientContainer = clients.get(clientIndex);
                    final Client client = clientContainer.client;
                    while (!client.offer(buffer, 0, payloadSize, ContentType.BINARY))
                    {
                        client.doWork();
                        backPressureCount++;
                    }
                }
                final long endOfSendTime = startTime + interBatchInterval - 60L;
                while (System.nanoTime() < endOfSendTime)
                {
                    for (int clientIndex = 0; clientIndex < clients.size(); clientIndex++)
                    {
                        clients.get(clientIndex).client.doWork();
                    }
                }
            }
            while (true)
            {
                boolean allComplete = true;
                for (int clientIndex = 0; clientIndex < clients.size(); clientIndex++)
                {
                    clients.get(clientIndex).client.doWork();
                    if (clients.get(clientIndex).handler.histogram.getTotalCount() < totalBatchCount)
                    {
                        allComplete = false;
                        break;
                    }
                }
                if (allComplete)
                {
                    break;
                }
            }
            for (int clientIndex = 0; clientIndex < clients.size(); clientIndex++)
            {
                resultHistogram.add(clients.get(clientIndex).handler.histogram);
                clients.get(clientIndex).handler.histogram.reset();
            }
        }
        return backPressureCount;
    }

    private static final class ClientAndHandler
    {
        private final Client client;
        private final MeasuringClientEventHandler handler;

        ClientAndHandler(final Client client, final MeasuringClientEventHandler handler)
        {
            this.client = client;
            this.handler = handler;
        }
    }

    private static final class MeasuringClientEventHandler implements ClientEventHandler
    {
        private final Histogram histogram;
        private final long highestTrackableValue;
        private final long expectedIntervalNs;

        private MeasuringClientEventHandler(final Histogram histogram, final long expectedIntervalNs)
        {
            this.histogram = histogram;
            highestTrackableValue = histogram.getHighestTrackableValue();
            this.expectedIntervalNs = expectedIntervalNs;
        }

        @Override
        public void onMessage(
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final ContentType contentType)
        {
            final long timestampNs = buffer.getLong(0);
            final long currentNanos = System.nanoTime();
            final long rttNanos = currentNanos - timestampNs;
            if (rttNanos < 0)
            {
                throw new IllegalStateException(String.format("Start time %d < now %d", timestampNs, currentNanos));
            }
            else
            {
                histogram.recordValueWithExpectedInterval(
                    Math.min(highestTrackableValue, rttNanos), expectedIntervalNs);
            }
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

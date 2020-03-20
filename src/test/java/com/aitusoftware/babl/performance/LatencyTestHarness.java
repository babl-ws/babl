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

import static com.google.common.truth.Truth.assertThat;

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

final class LatencyTestHarness
{
    private final int clientCount;
    private final int threadCount;
    private final int messagesPerSecond;
    private final int measurementIterations;
    private final int warmUpIterations;
    private final int messagesPerIteration;
    private final int payloadSize;

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
    }

    Histogram runLatencyTest(final InetSocketAddress serverAddress)
        throws InterruptedException, IOException, TimeoutException, ExecutionException
    {
        final ExecutorService threadPool = Executors.newFixedThreadPool(threadCount);
        final long interMessageInterval = TimeUnit.SECONDS.toNanos(1) / messagesPerSecond;
        final long expectedRuntimeNs = (warmUpIterations + measurementIterations) *
            (messagesPerIteration * interMessageInterval);
        final List<List<ClientAndHandler>> clientSets =
            distributeClients(serverAddress, clientCount, threadCount, interMessageInterval);
        final Histogram allResults = latencyHistogram();
        final List<Future<Histogram>> histogramResults = new ArrayList<>();
        for (int i = 0; i < threadCount; i++)
        {
            final List<ClientAndHandler> clientSet = clientSets.get(i);
            final Future<Histogram> measurementResult = executeLoad(threadPool, warmUpIterations,
                messagesPerIteration, measurementIterations, payloadSize, interMessageInterval, clientSet);
            histogramResults.add(measurementResult);
        }
        for (final Future<Histogram> histogramResult : histogramResults)
        {
            final Histogram histogram = histogramResult.get(20 * expectedRuntimeNs, TimeUnit.NANOSECONDS);
            allResults.add(histogram);
        }
        threadPool.shutdown();
        assertThat(threadPool.awaitTermination(20 * expectedRuntimeNs, TimeUnit.NANOSECONDS)).isTrue();
        allResults.outputPercentileDistribution(System.out, 1d);
        return allResults;
    }

    private static final AtomicInteger GENERATOR_ID = new AtomicInteger();

    private Future<Histogram> executeLoad(
        final ExecutorService threadPool, final int warmupIterations,
        final int messagesPerIteration, final int measurementIterations,
        final int payloadSize, final long interMessageInterval,
        final List<ClientAndHandler> clientSet)
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
                    payloadSize, buffer, interMessageInterval);
                resultHistogram.reset();
                System.out.printf("%s warmup complete%n", Thread.currentThread().getName());
                System.out.flush();
                deliverMessageBatches(resultHistogram, clientSet,
                    measurementIterations, messagesPerIteration,
                    payloadSize, buffer, interMessageInterval);

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

    private List<List<ClientAndHandler>> distributeClients(
        final InetSocketAddress serverAddress, final int clientCount,
        final int clientThreadCount, final long expectedIntervalNs) throws IOException
    {
        final List<List<ClientAndHandler>> clientSets = new ArrayList<>();
        for (int i = 0; i < clientThreadCount; i++)
        {
            clientSets.add(new ArrayList<>());
        }
        final URL url = new URL(String.format("http://%s:%s/foo",
            serverAddress.getHostString(), serverAddress.getPort()));

        for (int i = 0; i < clientCount; i++)
        {
            final Histogram histogram = latencyHistogram();
            final MeasuringClientEventHandler eventHandler = new MeasuringClientEventHandler(histogram,
                expectedIntervalNs);
            final Client client = new Client(eventHandler);
            client.connect(url);
            assertThat(client.upgradeConnection()).isTrue();
            clientSets.get(i % clientThreadCount).add(new ClientAndHandler(client, eventHandler));
        }
        return clientSets;
    }

    private void deliverMessageBatches(
        final Histogram resultHistogram,
        final List<ClientAndHandler> clients,
        final int iterations, final int messagesPerIteration,
        final int payloadSize, final MutableDirectBuffer buffer,
        final long interMessageInterval) throws IOException
    {
        int remainingIterations = iterations;
        while (remainingIterations-- != 0)
        {
            int remainingMessages = messagesPerIteration;
            while (remainingMessages-- != 0)
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
                    }
                }
                final long endOfSendTime = startTime + interMessageInterval - 60L;
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
                    if (clients.get(clientIndex).handler.histogram.getTotalCount() < messagesPerIteration)
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
            final long rttNanos = System.nanoTime() - timestampNs;
            if (rttNanos < 0)
            {
                throw new IllegalStateException();
            }
            else
            {
                histogram.recordValueWithExpectedInterval(
                    Math.min(highestTrackableValue, rttNanos), expectedIntervalNs);
            }
        }

        @Override
        public void onWebSocketConnectionEstablished()
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

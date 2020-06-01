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
package com.aitusoftware.babl.websocket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.aitusoftware.babl.config.SessionConfig;
import com.aitusoftware.babl.config.SessionContainerConfig;
import com.aitusoftware.babl.io.WebSocketPoller;
import com.aitusoftware.babl.log.Category;
import com.aitusoftware.babl.log.Logger;
import com.aitusoftware.babl.monitoring.MappedSessionContainerStatistics;
import com.aitusoftware.babl.monitoring.ServerMarkFile;
import com.aitusoftware.babl.monitoring.SessionContainerStatistics;
import com.aitusoftware.babl.monitoring.SessionStatisticsFileManager;
import com.aitusoftware.babl.pool.BufferPool;
import com.aitusoftware.babl.pool.ObjectPool;
import com.aitusoftware.babl.time.SingleThreadedCachedClock;
import com.aitusoftware.babl.user.Application;

import org.agrona.CloseHelper;
import org.agrona.collections.Hashing;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.SystemEpochClock;
import org.agrona.concurrent.errors.DistinctErrorLog;
import org.agrona.concurrent.errors.LoggingErrorHandler;

final class SessionContainer implements Agent, AutoCloseable
{
    private static final long ADMIN_WORK_SERVICE_INTERVAL_MS = 10L;
    private static final int INITIAL_SESSION_COUNT = 512;

    private final Queue<SocketChannel> incomingConnections;
    private final int sessionContainerId;
    private final Application application;
    private final WebSocketPoller webSocketPoller;
    private final BufferPool bufferPool = new BufferPool();
    private final boolean pollModeEnabled;
    private final int pollModeSessionLimit;
    private final SessionContainerConfig sessionContainerConfig;
    private final TrackingSessionDataListener sessionDataListener = new TrackingSessionDataListener();
    private final Long2ObjectHashMap<WebSocketSession> activeSessionMap =
        new Long2ObjectHashMap<>(INITIAL_SESSION_COUNT, Hashing.DEFAULT_LOAD_FACTOR);
    private final SystemEpochClock clock = new SystemEpochClock();
    private final SingleThreadedCachedClock sharedClock = new SingleThreadedCachedClock();
    private final LongHashSet invalidSessionsForRemoval = new LongHashSet();
    private final SessionContainerStatistics sessionContainerStatistics;
    private final ServerMarkFile serverMarkFile;
    private final ObjectPool<ConnectionUpgrade> connectionUpgradePool;
    private final ObjectPool<WebSocketSession> sessionPool;
    private final Agent additionalWork;
    private final SessionIdGenerator sessionIdGenerator;
    private final ConnectionValidator connectionValidator;
    private final Deque<WebSocketSession> queuedSessions = new ArrayDeque<>(32);
    private final Long2ObjectHashMap<WebSocketSession> notYetValidatedSessionMap =
        new Long2ObjectHashMap<>(INITIAL_SESSION_COUNT, Hashing.DEFAULT_LOAD_FACTOR);
    private final ObjectPool<ValidationResult> validationResultPool = new ObjectPool<>(ValidationResult::new, 64);
    private final ManyToOneConcurrentArrayQueue<ValidationResult> incomingValidationResults =
        new ManyToOneConcurrentArrayQueue<>(64);
    private final long validationTimeoutMs;
    private final Consumer<ValidationResult> validationResultHandler = new ValidationResultHandler();
    private long lastServiceTimeMs;
    private AgentRunner serverAgentRunner;

    /**
     * Constructs a web socket server that will dispatch messages to the supplied {@code Application}.
     *
     * @param sessionContainerId     identifier for this instance
     * @param application            the application that will process inbound messages
     * @param sessionConfig          configuration for web socket sessions
     * @param sessionContainerConfig configuration for the web socket server
     * @param additionalWork         extra work to be invoked on the event-loop
     * @param incomingConnections    queue to poll for new connections
     */
    SessionContainer(
        final int sessionContainerId,
        final Application application,
        final SessionConfig sessionConfig,
        final SessionContainerConfig sessionContainerConfig,
        final Agent additionalWork,
        final Queue<SocketChannel> incomingConnections)
    {
        this.sessionContainerId = sessionContainerId;
        this.application = application;
        this.sessionContainerConfig = sessionContainerConfig;
        this.pollModeEnabled = sessionContainerConfig.pollModeEnabled();
        this.pollModeSessionLimit = sessionContainerConfig.pollModeSessionLimit();
        validateMessageConfig(sessionConfig);
        final Path serverDirectory = Paths.get(sessionContainerConfig.serverDirectory(sessionContainerId));
        ensureDirectoryExists(serverDirectory);
        this.serverMarkFile = new ServerMarkFile(serverDirectory);
        this.sessionContainerStatistics = new MappedSessionContainerStatistics(
            serverMarkFile.serverStatisticsBuffer(), 0);
        final SessionStatisticsFileManager sessionStatisticsManager = new SessionStatisticsFileManager(
            serverDirectory, 1, sessionContainerConfig.sessionMonitoringFileEntryCount());
        sessionPool = new ObjectPool<>(new SessionFactory(
            sessionConfig, bufferPool, sharedClock, application, sessionContainerStatistics,
            sessionStatisticsManager, sessionDataListener), INITIAL_SESSION_COUNT);
        this.additionalWork = additionalWork;
        this.incomingConnections = incomingConnections;
        this.sessionIdGenerator = new SessionIdGenerator(sessionContainerId);
        webSocketPoller = new WebSocketPoller(this::notifySessionHasReadDataPending,
            sessionContainerConfig.sessionPollLimit());
        connectionValidator = sessionContainerConfig.connectionValidator();
        connectionUpgradePool = new ObjectPool<>(() -> new ConnectionUpgrade(validationResultPool, connectionValidator,
            this::connectionValidationResult), 64);
        validationTimeoutMs = TimeUnit.NANOSECONDS.toMillis(sessionContainerConfig.validationTimeoutNanos());
    }

    /**
     * Constructs a web-socket server that will dispatch messages to the supplied {@code Application}.
     *
     * @param application   the application that will process inbound messages
     * @param sessionConfig configuration for web socket sessions
     * @param sessionContainerConfig  configuration for the web socket server
     */
    SessionContainer(
        final Application application,
        final SessionConfig sessionConfig,
        final SessionContainerConfig sessionContainerConfig,
        final Queue<SocketChannel> incomingConnections)
    {
        this(0, application, sessionConfig, sessionContainerConfig, null, incomingConnections);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int doWork() throws Exception
    {
        final long timeMs = clock.time();
        sharedClock.set(timeMs);
        int workCount = 0;
        if (!queuedSessions.isEmpty())
        {
            final WebSocketSession queued = queuedSessions.pollLast();
            workCount += attemptSessionConnected(queued);
        }

        final SocketChannel newConnection = incomingConnections.poll();
        if (newConnection != null)
        {
            final long sessionId = sessionIdGenerator.nextSessionId();
            if (activeSessionMap.containsKey(sessionId))
            {
                CloseHelper.quietClose(newConnection);
                return 1;
            }
            final WebSocketSession session = sessionPool.acquire();
            session.init(sessionId, connectionUpgradePool.acquire(), this::connectionUpgraded, timeMs,
                newConnection, newConnection);
            workCount++;
            notYetValidatedSessionMap.put(sessionId, session);
            webSocketPoller.register(session, newConnection);
            activeSessionMap.put(sessionId, session);
            sessionContainerStatistics.activeSessionCount(activeSessionMap.size());
        }
        workCount += sendData();
        workCount += receiveData();
        workCount += removeInactiveSessions();
        workCount += doAdminWork(timeMs);
        final long eventLoopDurationMs = clock.time() - timeMs;
        sessionContainerStatistics.eventLoopDurationMs(eventLoopDurationMs);
        return workCount;
    }

    /**
     * Starts the web socket server.
     */
    public void start()
    {
        sessionContainerConfig.bufferPoolPreAllocator().preAllocate(bufferPool);
        final DistinctErrorLog errorLog = new DistinctErrorLog(serverMarkFile.errorBuffer(), clock);
        final LoggingErrorHandler errorHandler = new LoggingErrorHandler(errorLog);
        // process additional work Agent first before the Server Agent, which will read more data from the network
        final Agent serverWork = additionalWork == null ? this : new DoubleAgent(additionalWork, this);
        serverAgentRunner = new AgentRunner(sessionContainerConfig.serverIdleStrategy(sessionContainerId),
            errorHandler, null, serverWork);
        AgentRunner.startOnThread(serverAgentRunner, sessionContainerConfig.threadFactory());
    }

    /**
     * Closes the web socket server, terminating its threads.
     */
    @Override
    public void close()
    {
        CloseHelper.close(serverAgentRunner);
        CloseHelper.close(serverMarkFile);
        CloseHelper.close(bufferPool);
        if (additionalWork != null)
        {
            additionalWork.onClose();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String roleName()
    {
        return "babl-server";
    }

    SessionContainerStatistics sessionContainerStatistics()
    {
        return sessionContainerStatistics;
    }

    private int doAdminWork(final long timeMs)
    {
        int workCount = 0;
        if (timeMs > lastServiceTimeMs + ADMIN_WORK_SERVICE_INTERVAL_MS)
        {
            sessionContainerStatistics.heartbeat(timeMs);
            lastServiceTimeMs = timeMs;
            final Long2ObjectHashMap<WebSocketSession>.ValueIterator activeSessions =
                activeSessionMap.values().iterator();
            while (activeSessions.hasNext())
            {
                workCount += activeSessions.next().doAdminWork();
            }
            final Long2ObjectHashMap<WebSocketSession>.ValueIterator notYetValidatedSessions =
                notYetValidatedSessionMap.values().iterator();

            invalidSessionsForRemoval.clear();
            while (notYetValidatedSessions.hasNext())
            {
                final WebSocketSession session = notYetValidatedSessions.next();
                if (session.connectedTimestampMs() < timeMs - validationTimeoutMs)
                {
                    invalidSessionsForRemoval.add(session.id());
                }
            }
            final LongHashSet.LongIterator sessionsForRemoval = invalidSessionsForRemoval.iterator();
            while (sessionsForRemoval.hasNext())
            {
                final long sessionId = sessionsForRemoval.nextValue();
                removeInactiveSession(sessionId);
            }
            workCount += pollIncomingValidationResults();
        }
        return workCount;
    }

    private int removeInactiveSessions()
    {
        int workCount = 0;
        final LongHashSet toRemove = sessionDataListener.toRemove;
        if (!toRemove.isEmpty())
        {
            final LongHashSet.LongIterator removeIterator = toRemove.iterator();
            while (removeIterator.hasNext())
            {
                final long sessionId = removeIterator.nextValue();
                removeInactiveSession(sessionId);
                workCount++;
            }
            toRemove.clear();
        }
        return workCount;
    }

    private void removeInactiveSession(final long sessionId)
    {
        notYetValidatedSessionMap.remove(sessionId);
        sessionDataListener.receiveWorkAvailable.remove(sessionId);
        sessionDataListener.sendWorkAvailable.remove(sessionId);
        final WebSocketSession session = activeSessionMap.remove(sessionId);
        sessionPool.release(session);

        sessionContainerStatistics.activeSessionCount(activeSessionMap.size());
    }

    private int sendData() throws IOException
    {
        int workCount = 0;
        final LongHashSet.LongIterator iterator = sessionDataListener.sendWorkAvailable.iterator();
        while (iterator.hasNext())
        {
            final long sendSessionId = iterator.nextValue();
            final WebSocketSession session = activeSessionMap.get(sendSessionId);
            workCount += session.doSendWork();
        }
        return workCount;
    }

    private int receiveData() throws Exception
    {
        int workCount = 0;
        if (!pollModeEnabled || activeSessionMap.size() > pollModeSessionLimit)
        {
            workCount += webSocketPoller.doWork();
            final LongHashSet.LongIterator iterator = sessionDataListener.receiveWorkAvailable.iterator();
            while (iterator.hasNext())
            {
                final long receiveSessionId = iterator.nextValue();
                final WebSocketSession session = activeSessionMap.get(receiveSessionId);
                workCount += session.doReceiveWork();
            }
        }
        else
        {
            final Long2ObjectHashMap<WebSocketSession>.KeyIterator iterator = activeSessionMap.keySet().iterator();
            while (iterator.hasNext())
            {
                final long receiveSessionId = iterator.nextLong();
                final WebSocketSession session = activeSessionMap.get(receiveSessionId);
                workCount += session.doReceiveWork();
            }
        }
        return workCount;
    }

    private void notifySessionHasReadDataPending(final WebSocketSession session)
    {
        sessionDataListener.receiveDataAvailable(session.id());
    }

    private void connectionUpgraded(final ConnectionUpgrade connectionUpgrade)
    {
        connectionUpgradePool.release(connectionUpgrade);
    }

    private int attemptSessionConnected(final WebSocketSession queued)
    {
        int workDone = 0;
        if (SendResult.OK != application.onSessionConnected(queued))
        {
            queuedSessions.addLast(queued);
        }
        else
        {
            workDone++;
        }
        return workDone;
    }

    private int pollIncomingValidationResults()
    {
        return incomingValidationResults.drain(validationResultHandler, 8);
    }

    private void onIncomingValidationResult(final ValidationResult validationResult)
    {
        validationResultPool.release(validationResult);
        final WebSocketSession session = notYetValidatedSessionMap.remove(validationResult.sessionId());
        if (session != null)
        {
            Logger.log(Category.CONNECTION, "Session %d validation result code: %d%n",
                validationResult.sessionId(), validationResult.resultCode());
            if (validationResult.resultCode() == ValidationResult.CONNECTION_VALID)
            {
                session.validated();
                queuedSessions.add(session);
            }
            else
            {
                session.onCloseMessage(validationResult.resultCode());
            }
        }
    }

    private boolean connectionValidationResult(final ValidationResult validationResult)
    {
        return incomingValidationResults.offer(validationResult);
    }

    private static void validateMessageConfig(final SessionConfig sessionConfig)
    {
        if (sessionConfig.maxWebSocketFrameLength() > sessionConfig.maxBufferSize() / 2)
        {
            throw new IllegalStateException("SessionConfig maxBufferSize must be at least 2 * maxWebSocketFrameLength");
        }
    }

    ServerMarkFile serverMarkFile()
    {
        return serverMarkFile;
    }

    private static void ensureDirectoryExists(final Path serverDirectory)
    {
        try
        {
            Files.createDirectories(serverDirectory);
        }
        catch (final IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static final class DoubleAgent implements Agent
    {
        private final Agent one;
        private final Agent two;
        private final String roleName;

        DoubleAgent(final Agent one, final Agent two)
        {
            this.one = one;
            this.two = two;
            this.roleName = "[" + one.roleName() + "," + two.roleName() + "]";
        }

        @Override
        public int doWork() throws Exception
        {
            return one.doWork() + two.doWork();
        }

        @Override
        public String roleName()
        {
            return roleName;
        }

        @Override
        public void onStart()
        {
            one.onStart();
            two.onStart();
        }

        @Override
        public void onClose()
        {
            one.onClose();
            two.onClose();
        }
    }

    private final class ValidationResultHandler implements Consumer<ValidationResult>
    {
        @Override
        public void accept(final ValidationResult validationResult)
        {
            onIncomingValidationResult(validationResult);
        }
    }
}
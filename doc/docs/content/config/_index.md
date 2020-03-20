+++
title = "Configuration"
weight = 7
+++

<style>
    table {
        width: 100%
    }
</style>

## Session Container

Configures the operation of the server as a whole.

|  | Session Container Configuration Options |
| --- | --- |
|  | **Directory (`String`)** |
| **Description** | The working directory for the web-socket server. Monitoring files will be created in this directory |
| **Default** | `$TMP/babl-server` |
| **Property** | `babl.server.directory` |
| **API** | `SessionContainerConfig.serverDirectory` |
|  |  |
|  | **Bind Address (`String`)** |
| **Description** | The hostname or IP address that the server will listen on for inbound TCP connections |
| **Default** | `0.0.0.0` |
| **Property** | `babl.server.bind.address` |
| **API** | `SessionContainerConfig.bindAddress` |
|  |  |
|  | **Listen Port (`String`)** |
| **Description** | The port that the server will listen on for inbound TCP connections |
| **Default** | `8080` |
| **Property** | `babl.server.listen.port` |
| **API** | `SessionContainerConfig.listenPort` |
|  |  |
|  | **Connection Backlog (`int`)** |
| **Description** | The maximum number of pending connections |
| **Default** | `20` |
| **Property** | `babl.server.connection.backlog` |
| **API** | `SessionContainerConfig.connectionBacklog` |
|  |  |
|  | **Poll Mode Enabled (`boolean`)** |
| **Description** | Enables poll-mode when the number of active sessions is below a configurable limit |
| **Default** | `false` |
| **Property** | `babl.server.poll.mode.enabled` |
| **API** | `SessionContainerConfig.pollModeEnabled` |
|  |  |
|  | **Poll Mode Session Limit (`int`)** |
| **Description** | Receive sockets will be polled as part of the event-loop (rather than using `select()`) if the active session count is below or at this value |
| **Default** | `5` |
| **Property** | `babl.server.poll.mode.session.limit` |
| **API** | `SessionContainerConfig.pollModeSessionLimit` |
|  |  |
|  | **Session Monitoring File Entry Count (`int`)** |
| **Description** | Sets the maximum number of sessions per monitoring file (monitoring files will be created as needed) |
| **Default** | `4096` |
| **Property** | `babl.server.session.monitoring.entry.count` |
| **API** | `SessionContainerConfig.sessionMonitoringFileEntryCount` |
|  |  |
|  | **Session Poll Limit (`int`)** |
| **Description** | Sets the maximum number of sessions that will be ready for reading per invocation of the server event-loop |
| **Default** | `200` |
| **Property** | `babl.server.session.poll.limit` |
| **API** | `SessionContainerConfig.sessionPollLimit` |
|  |  |
|  | **Connection Validator (`String`)** |
| **Description** | Fully-qualified classname of an implementation of `ConnectionValidator` |
| **Default** | `AlwaysValidConnectionValidator` |
| **Property** | `babl.server.validation.validator` |
| **API** | `SessionContainerConfig.connectionValidator` |
|  |  |
|  | **Validation Timeout Nanos (`long`)** |
| **Description** | Sets the timeout for connections to be validated |
| **Default** | `10s` |
| **Property** | `babl.server.validation.timeout` |
| **API** | `SessionContainerConfig.validationTimeoutNanos` |
|  |  |
|  | **Server Idle Strategy (`String`)** |
| **Description** | Sets the idling strategy used in the Server event-loop. One of `BUSY_SPIN,YIELDING,BACK_OFF,SLEEPING`. |
| **Default** | `SLEEPING` |
| **Property** | `babl.server.idle.strategy` |
| **API** | `SessionContainerConfig.serverIdleStrategy` |

## Session

Configure per-session settings.

|  | Session Configuration Options |
| --- | --- |
|  | **Maximum Buffer Size (`int`)** |
| **Description** | The maximum size, in bytes that the session's send or receive buffer can grow to |
| **Default** | `32MB` |
| **Property** | `babl.session.buffer.max.size` |
| **API** | `SessionConfig.maxBufferSize` |
|  |  |
|  | **Receive Buffer Size (`int`)** |
| **Description** | The initial size, in bytes of the session's receive buffer (used for encoded web socket frames read from the network) |
| **Default** | `1KB` |
| **Property** | `babl.session.buffer.receive.size` |
| **API** | `SessionConfig.receiveBufferSize` |
|  |  |
|  | **Send Buffer Size (`int`)** |
| **Description** | The initial size, in bytes of the session's send buffer (used for web socket frames queued for writing to the network) |
| **Default** | `1KB` |
| **Property** | `babl.session.buffer.send.size` |
| **API** | `SessionConfig.sendBufferSize` |
|  |  |
|  | **Decode Buffer Size (`int`)** |
| **Description** | The initial size, in bytes of the session's decode buffer (used for assembling decoded web socket frames before delivery to the application) |
| **Default** | `1KB` |
| **Property** | `babl.session.buffer.decode.size` |
| **API** | `SessionConfig.sessionDecodeBufferSize` |
|  |  |
|  | **Decode Maximum Buffer Size (`int`)** |
| **Description** | The maximum size, in bytes of the session's decode buffer (used for assembling decoded web socket frames before delivery to the application) |
| **Default** | `128KB` |
| **Property** | `babl.session.buffer.decode.max.size` |
| **API** | `SessionConfig.sessionDecodeBufferMaxSize` |
|  |  |
|  | **Maximum Web Socket Frame Length (`int`)** |
| **Description** | The maximum length, in bytes of the largest acceptable web socket frame |
| **Default** | `64KB` |
| **Property** | `babl.session.frame.max.size` |
| **API** | `SessionConfig.maxWebSocketFrameLength` |
|  |  |
|  | **Ping Send Interval (`long`)** |
| **Description** | The time, in nanoseconds that an idle session will wait before sending a `PING` frame to its peer |
| **Default** | `5s` |
| **Property** | `babl.session.ping.interval` |
| **API** | `SessionConfig.pingIntervalNanos` |
|  |  |
|  | **Pong Response Timeout (`long`)** |
| **Description** | The time, in nanoseconds that a session will wait before a `PONG` response before closing |
| **Default** | `30s` |
| **Property** | `babl.session.pong.response.timeout` |
| **API** | `SessionConfig.pongResponseTimeoutNanos` |

## Socket

Configures network socket settings.

|  | Socket Configuration Options |
| --- | --- |
|  | **Send Buffer Size (`int`)** |
| **Description** | The size, in bytes of the session's socket send buffer (`SO_SND_BUF`) |
| **Default** | `64KB` |
| **Property** | `babl.socket.send.buffer.size` |
| **API** | `SocketConfig.sendBufferSize` |
|  |  |
|  | **Receive Buffer Size (`int`)** |
| **Description** | The size, in bytes of the session's socket receive buffer (`SO_RCV_BUF`) |
| **Default** | `64KB` |
| **Property** | `babl.socket.receive.buffer.size` |
| **API** | `SocketConfig.receiveBufferSize` |
|  |  |
|  | **TCP No Delay Enabled (`boolean`)** |
| **Description** | Indicates whether Nagle's Algorithm should be disabled (`SO_TCP_NODELAY`) |
| **Default** | `false` |
| **Property** | `babl.socket.tcpNoDelay.enabled` |
| **API** | `SocketConfig.tcpNoDelay` |

## Proxy

Configures the operation of the IPC transport between the session host and the application.

Only used when server deployment mode is `DETACHED`.

|  | Proxy Configuration Options |
| --- | --- |
|  | **Server Adapter Poll Fragment Limit (`int`)** |
| **Description** | The maximum number of fragments processed by the server adapter for a single `poll` call |
| **Default** | `50` |
| **Property** | `babl.proxy.server.adapter.poll.limit` |
| **API** | `ProxyConfig.serverAdapterPollFragmentLimit` |
|  |  |
|  | **Application Adapter Poll Fragment Limit (`int`)** |
| **Description** | The maximum number of fragments processed by the application adapter for a single `poll` call |
| **Default** | `40` |
| **Property** | `babl.proxy.application.adapter.poll.limit` |
| **API** | `ProxyConfig.applicationAdapterPollFragmentLimit` |
|  |  |
|  | **Server Stream Base ID (`int`)** |
| **Description** | The starting stream ID for server instances |
| **Default** | `6000` |
| **Property** | `babl.proxy.server.stream.base.id` |
| **API** | `ProxyConfig.serverStreamBaseId` |
|  |  |
|  | **Application Stream Base ID (`int`)** |
| **Description** | The starting stream ID for application instances |
| **Default** | `5000` |
| **Property** | `babl.proxy.application.stream.base.id` |
| **API** | `ProxyConfig.applicationStreamBaseId` |
|  |  |
|  | **Launch Media Driver (`boolean`)** |
| **Description** | Indicates whether an Aeron MediaDriver should be launched for use by proxies |
| **Default** | `false` |
| **Property** | `babl.proxy.driver.launch` |
| **API** | `ProxyConfig.launchMediaDriver` |
|  |  |
|  | **Media Driver Directory (`String`)** |
| **Description** | Indicates where the MediaDriver should be launched |
| **Default** | `${java.io.tmpdir}/proxy-driver` |
| **Property** | `babl.proxy.driver.dir` |
| **API** | `ProxyConfig.mediaDriverDir` |
|  |  |
|  | **Performance Mode (`String`)** |
| **Description** | Configures the performance level of the launched MediaDriver |
| **Default** | `PerformanceMode.LOW` |
| **Property** | `babl.proxy.performance.mode` |
| **API** | `ProxyConfig.performanceMode` |

## JVM Settings

For best performance, set the following system properties:

   * `-Djava.lang.Integer.IntegerCache.high=65536`
   * `-Djava.net.preferIPv4Stack=true`
   * `-XX:+UnlockExperimentalVMOptions -XX:+TrustFinalNonStaticFields`
   * `-XX:BiasedLockingStartupDelay=0`
   
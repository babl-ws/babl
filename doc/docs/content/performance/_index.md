+++
title = "Performance"
weight = 9
+++

This page describes settings that can be applied to increase the performance of **Babl** server.

_Any improvement in performance should be measured against a baseline without these settings
enabled to ensure that they have a measurable and useful effect_.

Configuration items below can either be set on the command-line, or included in the 
config file passed to the `Server` program (in this case, without their `-D` prefix).

## JVM Arguments

### Disable Buffer Bounds Checking

When rigorous testing has been completed, and an application has been deployed to production,
buffer bounds-checking can be disabled for an extra performance boost.

Any performance gain is expected to only be a few percent. Any advantage should be measured and
weighed up against the possible downsides 
(an incorrect buffer read in the application will cause a segmentation fault and program crash).

Disable bounds-checking with this JVM parameter:

```
-Dagrona.disable.bounds.checks=true
```

### Cache `java.lang.Integer` Objects

Deep in the heart of the Java IO libraries, `java.lang.Integer` variables are used to 
represent file-descriptors. Depending on how many sockets are created on the system in use,
the value presented below may need to be increased to reduce allocation.

Cache a fixed number of `java.lang.Integer` values with this JVM parameter:

```
-Djava.lang.Integer.IntegerCache.high=65536
```  

### Prefer IPv4 Network Stack

A hint to the JVM that it should not default to using IPv6 even when the system supports it.

Enable with this JVM parameter:

```
-Djava.net.preferIPv4Stack=true
```

### Trust Non-Static Final Fields

A hint to the JIT compiler that `final` fields can be trusted as final (i.e. subject to certain optimisations).

Enable with these JVM parameters:

```
-XX:+UnlockExperimentalVMOptions -XX:+TrustFinalNonStaticFields
```

### Immediate Biased Locking

Perform biased locking immediately. Older JVMs wait several seconds before attempting to bias locks.
Since **Babl** is inherently single-threaded when dealing with `synchronized` objects 
(e.g. `java.nio.SocketChannel`), lock biasing should be performed at start-up.

This setting may have negligible effect on JDK9+, as intrinsic locks were replaced with 
`java.util.concurrent.ReentrantLock` instances in the `java.nio` socket components.

Enable with this JVM parameter:

```
-XX:BiasedLockingStartupDelay=0
```

## Babl Configuration

### Disable Nagle's Algorithm

Setting this flag will tell the operating system to send TCP packets as soon as they are queued,
rather than waiting for more data to improve batching.

```
-Dbabl.socket.tcpNoDelay.enabled=true
```

### Use Busy-spin in Server Event-loop

Uses a busy-spin wait-loop when processing the Server event-loop. 
Setting this property can reduce latency between a message being received from a client,
and being passed to the application. Setting this property will cause more CPU usage.

```
-Dbabl.server.idle.strategy=BUSY_SPIN
```

### Use Busy-spin in IPC Transport

Uses a busy-spin wait-loop when communicating with the IPC transport.
Setting this property will cause more CPU usage.

```
-Dbabl.proxy.performance.mode=HIGH
```

### Place IPC shared-memory files on `/dev/shm`

Reduces the chance of page-faults.

```
-Dbabl.proxy.driver.dir=/dev/shm/babl
```
+++
title = "Getting Started"
weight = 2
+++

**Babl** is a high-performance, scalable web-socket server designed for use in low-latency applications.

Built from the ground up to provide blazing fast execution speeds, and a novel auto-scaling mechanism,
**Babl** will comfortably handle high throughput, large session-count workloads without slowing down.

Using an event-driven reactive programming model, your application code is executed in an 
allocation-free, lock-free event-loop for maximum efficiency and mechanical sympathy.

## Your Application

**Babl** is a container for your application, providing a high-performance proxy for web-socket connections.

Your application will be notified of connection lifecycle events (`onConnect`, `onDisconnect`), and receive
messages (`onMessage`) from client connections.

Your application will publish responses to inbound messages, which will be sent back to the client.

For more information, see [Application](@/application/_index.md).

## Using Babl

### Docker

**Babl** is packaged in a `docker` container for ease of deployment. The base container is tagged as 
`"babl:BABL_VERSION"` (or `"babl:latest"` for the latest released image), 
and should be extended with your own container definition:

```
FROM babl:latest

COPY my-app/build/lib/ /babl/lib/
COPY my-app/build/config/my-app.properties /babl/config
ENV BABL_CONFIG_FILE="/babl/config/my-app.properties"
ENV JVM_RUNTIME_PARAMETERS="-Dbabl.debug.enabled=true"
```

The project source code contains an example `docker-compose` file that creates a simple echo application with HTML
resources hosted by an `nginx` instance.

### Standalone

**Babl** can also be used in a standalone manner. Simply download the `-all` variant of the JAR from 
the releases page, and execute the following:

```
$ java -jar /path/to/babl-all-BABL_VERSION.jar /path/to/config.properties
```

Note that there is no legacy HTTP server functionality, so any HTML resources will need to be 
hosted using a suitable technology.

For more information on configuration, see [Configuration](@/config/_index.md).
+++
title = "Application"
weight = 3
+++

At the heart of **Babl** server is your `Application` where your business logic is executed.

Your application is executed on a single thread, and will be invoked when any of the following events occur:

   * A new web-socket session is established
   * A message is received from an established session
   * A web-socket session is disconnected

When running in `DIRECT` mode, your application is invoked on the same thread responsible for 
processing network I/O and handling web-socket protocol framing. 

When running in `DETACHED` mode, your application is invoked on its own thread.

For more detail on the two different modes, see [Architecture](@/architecture/_index.md).
   
## The `Application` Interface

Your application must implement the `Application` interface, shown below.

```java
package com.aitusoftware.babl.user;

import com.aitusoftware.babl.websocket.DisconnectReason;
import com.aitusoftware.babl.websocket.Session;

import org.agrona.DirectBuffer;

public interface Application
{
    int onSessionConnected(
        Session session);

    int onSessionDisconnected(
        Session session,
        DisconnectReason reason);

    int onSessionMessage(
        Session session,
        ContentType contentType,
        DirectBuffer msg,
        int offset,
        int length);
}
```

## The Echo Application

The simplest application we can write is simply to echo back messages to the client connection.

This application is deployed in the example container described in [Getting Started](@/getting_started/_index.md).

```java
public final class EchoApplication implements Application
{
    private final MutableDirectBuffer buffer = 
        new ExpandableDirectByteBuffer(512);

    @Override
    public int onSessionConnected(
        final Session session)
    {
        System.out.printf("Session %d connected%n", session.id());
        return SendResult.OK;
    }

    @Override
    public int onSessionDisconnected(
        final Session session, 
        final DisconnectReason reason)
    {
        System.out.printf("Session %d disconnected due to %s%n", 
            session.id(), reason.name());
        return SendResult.OK;
    }

    @Override
    public int onSessionMessage(
        final Session session,
        final ContentType contentType,
        final DirectBuffer msg,
        final int offset,
        final int length)
    {
        // copy request data to outbound buffer
        buffer.putBytes(0, msg, offset, length);
        int sendResult;
        do
        {
            // send buffer to session
            sendResult = session.send(contentType, buffer, 0, length);
        }
        while (sendResult != SendResult.OK);

        return sendResult;
    }
}
```
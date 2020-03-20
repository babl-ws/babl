+++
title = "Session Lifecycle"
weight = 5
+++

When a new socket connection is made, a `Session` will step through several states.

## `UPGRADING`

The `Connection: Upgrade` directive is processed, and the initial `HTTP` connection is 
upgraded to the web-socket protocol.

## `VALIDATING`

Once the connection has been upgraded, but before the application is notified of the new session,
the session must be _validated_. By default, all sessions will be validated, but **Babl** allows
a custom strategy to be defined to perform user-defined validation (for example an authentication operation).

As part of the validation step, all `HTTP` request headers are propagated from the connection
upgrade request. To provide custom validation, implement the `ConnectionValidator` interface:

```java
public interface ConnectionValidator
{
    void validateConnection(
        ValidationResult validationResult,
        Consumer<BiConsumer<CharSequence, CharSequence>> headerProvider,
        ValidationResultPublisher validationResultPublisher);
}
``` 

The validation will be invoked on the event-loop thread, so it is vital that any implementation of
this interface **does not block**. The recommended approach is to copy the supplied parameters to 
a queue, and perform the validation work on a separate thread.

Implementations should take note of the following:

   * The headers are provided as `CharSequence` objects to avoid allocation - references to these objects must not be retained
   * The `ValidationResult` should be mutated to indicate whether validation of the connection was successful - references to this object must not be retained
   * The `ValidationResultPublisher` is a thread-safe sink for the mutated `ValidationResult`

### Publishing the validation result

Validation is either successful:

```java
public void validateConnection(...) {
    validationResult.validationSuccess();
    while (!validationResultPublisher.publishResult(validationResult)) {
        // result queue full, need to retry after a short wait
    }
}
```

or unsuccessful:

```java
// Valid user codes are 4000-4999
static final short AUTHENTICATION_FAILED = 4007;

public void validateConnection(...) {
    validationResult.validationFailure(AUTHENTICATION_FAILED);
    while (!validationResultPublisher.publishResult(validationResult)) {
        // result queue full, need to retry after a short wait
    }
}
```

On validation success, the session will transition to the `CONNECTED` state and the application will be notified.

On validation failure, the connection will be closed.

The result code passed to `validationFailure` will be propagated to the 
web-socket client as the _close reason_.

## `CONNECTED`

Normal operation, web-socket messages will be passed to the application as they are received.

Messages sent to the `Session` will be framed and transmitted to the remote peer.

## `CLOSING`

Close has been requested by the application or the session container. The close frame will be sent to
the remote peer.

## `DISCONNECTED`

The session is no longer connected.
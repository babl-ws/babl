+++
title = "Monitoring"
weight = 8
+++

In order to maintain a constant memory footprint, **Babl** exports metrics to memory-mapped files that 
can be processed by external programs. This approach keeps the reporting of metrics fast, while also 
ensuring that no allocation occurs due to 3rd-party monitoring libraries.

The recommended approach is to read and interpret the exported monitoring data, and push to a 
metrics back-end for processing.

## Exported Metrics

### Server Statistics

Records server-wide statistics, contained in the server _mark-file_ `${SERVER_DIR}/babl-server.mark`.

Can be read using the program `com.aitusoftware.babl.monitoring.SessionContainerStatisticsPrinter`:

```
$ java -cp babl-BABL_VERSION-all.jar \
    com.aitusoftware.babl.monitoring.SessionContainerStatisticsPrinter \
    $SERVER_DIR
Timestamp: 2021-03-14T15:32:05.358Z
Bytes Read:                       42826384
Bytes Written:                    42621246
Active Sessions:                       176
Back Pressure Events:                    2
Invalid Opcode Events:                   0
Max Event Loop Ms    :                   1
```

   * _Timestamp_ - written by the server event-loop on each iteration, can be used as a liveness check
   * _Bytes Read_ - total number of bytes read from sockets
   * _Bytes Written_ - total number of bytes written to sockets
   * _Active Sessions_ - number of web-socket sessions currently connected
   * _Back Pressure Events_ - number of times the user `Application` could not keep up with incoming message rates
   * _Invalid Opcode Events_ - number of invalid web-socket frames received
   * _Max Event Loop Ms_ - max time (in milliseconds) spent processing all connected sessions

### Session Statistics

Records per-session statistics, contained in files named `${SERVER_DIR}/babl-session-statistics-N.data`.

Can be read using the program `com.aitusoftware.babl.monitoring.SessionStatisticsPrinter`:

```
$ java -cp babl-BABL_VERSION-all.jar \
    com.aitusoftware.babl.monitoring.SessionStatisticsPrinter \
    $SERVER_DIR
Session ID:                                 711
Session State:                        CONNECTED
Bytes Read:                              433878
Bytes Written:                           433397
Frames Decoded:                            1271
Frames Encoded:                            2043
Messages Received:                         1090
Messages Sent:                             2043
Receive Buffered Bytes:                       0
Send Buffered Bytes:                          0
Invalid Messages Received:                    0
Invalid Pings Received:                       0
Send Back Pressure Events:                    0
```

   * _Session ID_ - server-assigned ID of the session
   * _Session State_ - current state of the session
   * _Bytes Read_ - total number of bytes read from session's socket
   * _Bytes Written_ - total number of bytes written to session's socket
   * _Frames Decoded_ - total number of web-socket frames decoded
   * _Frames Encoded_ - total number of web-socket frames encoded
   * _Messages Received_ - total number of complete messages received
   * _Messages Sent_ - total number of complete messages sent
   * _Receive Buffered Bytes_ - snapshot of the number of bytes currently queued for processing
   * _Send Buffered Bytes_ - snapshot of the number of bytes currently queued for sending
   * _Invalid Messages Received_ - total number of invalid messages received on the session
   * _Invalid Pings Received_ - total number of invalid pings received on the session
   * _Send Back Pressure Events_ - number of times the server has been unable to write data to the remote peer

### Errors

Any exceptions are logged to an `ErrorBuffer` for efficient reporting, and can be viewed using the program
`com.aitusoftware.babl.monitoring.ErrorPrinter`:

```
$ java -cp babl-BABL_VERSION-all.jar \
    com.aitusoftware.babl.monitoring.ErrorPrinter \
    $SERVER_DIR
No errors reported.
```

## Docker

If running **Babl** in the supplied docker container, utility scripts are provided for viewing 
monitoring data:

   * `/babl/bin/babl_print_server_stats.sh`
   * `/babl/bin/babl_print_session_stats.sh`
   * `/babl/bin/babl_print_errors.sh`

If running the server in `DETACHED` mode, Aeron statistics can be displayed with:

   * `/babl/bin/babl_aeron_stat.sh`

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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.ServerWebSocket;

final class VertxServer implements AutoCloseable
{
    private final int port;
    private HttpServer httpServer;
    private Vertx vertx;

    VertxServer(final int port)
    {
        this.port = port;
    }

    void start() throws Exception
    {
        vertx = Vertx.vertx();
        httpServer = vertx.createHttpServer(new HttpServerOptions());
        httpServer.websocketHandler(new Handler<ServerWebSocket>()
        {
            @Override
            public void handle(final ServerWebSocket event)
            {
                event.frameHandler(new EchoWebSocketFrameHandler(event));
            }
        });
        final CountDownLatch latch = new CountDownLatch(1);
        httpServer.listen(port, new Handler<AsyncResult<HttpServer>>()
        {
            @Override
            public void handle(final AsyncResult<HttpServer> event)
            {
                latch.countDown();
            }
        });
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    }

    int actualPort()
    {
        return httpServer.actualPort();
    }

    @Override
    public void close() throws Exception
    {
        if (httpServer != null)
        {
            httpServer.close();
        }
        if (vertx != null)
        {
            vertx.close();
        }
    }
}

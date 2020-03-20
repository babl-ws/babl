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
package com.aitusoftware.babl.performance.resin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.aitusoftware.babl.performance.PortProbe;
import com.caucho.resin.HttpEmbed;
import com.caucho.resin.ResinEmbed;
import com.caucho.resin.ServletEmbed;
import com.caucho.resin.ServletMappingEmbed;
import com.caucho.resin.WebAppEmbed;
import com.caucho.websocket.WebSocketContext;
import com.caucho.websocket.WebSocketListener;
import com.caucho.websocket.WebSocketServletRequest;

public final class ResinWebSocketServer extends HttpServlet implements AutoCloseable
{
    private ResinEmbed server;


    public void start(final int port)
    {
        server = launch(port);
        PortProbe.ensurePortOpen(port);
    }

    @Override
    public void close() throws Exception
    {
        if (server != null)
        {
            server.close();
        }
    }

    public static ResinEmbed launch(final int port)
    {
        final ResinEmbed resin = new ResinEmbed();

        final HttpEmbed http = new HttpEmbed(port);
        resin.addPort(http);

        final WebAppEmbed webApp = new WebAppEmbed("/", "/tmp/resin");
        final ServletEmbed servlet = new ServletEmbed();
        servlet.setServletClass(ResinWebSocketServer.class.getName());
        servlet.setServletName("ws");
        webApp.addServlet(servlet);
        final ServletMappingEmbed mapping = new ServletMappingEmbed();
        mapping.setUrlPattern("/*");
        mapping.setServletName("ws");
        webApp.addServletMapping(mapping);
        resin.addWebApp(webApp);

        resin.start();

        return resin;
    }

    public void service(final HttpServletRequest req, final HttpServletResponse res) throws IOException
    {
        final WebSocketListener listener = new EchoListener();
        final WebSocketServletRequest wsReq = (WebSocketServletRequest)req;

        wsReq.startWebSocket(listener);
    }

    private static final class EchoListener implements WebSocketListener
    {
        private final byte[] buffer = new byte[4096];
        private final char[] cuffer = new char[4096];

        @Override
        public void onStart(final WebSocketContext context) throws IOException
        {
        }

        @Override
        public void onReadBinary(final WebSocketContext context, final InputStream is) throws IOException
        {
            final OutputStream outputStream = context.startBinaryMessage();
            int c;
            while ((c = is.read(buffer)) != -1)
            {
                outputStream.write(buffer, 0, c);
            }
            is.close();
            outputStream.close();
            context.flush();
        }

        @Override
        public void onReadText(final WebSocketContext context, final Reader is) throws IOException
        {
            final Writer outputStream = context.startTextMessage();
            int c;
            while ((c = is.read(cuffer)) != -1)
            {
                outputStream.write(cuffer, 0, c);
            }
            is.close();
            outputStream.close();
            context.flush();
        }

        @Override
        public void onClose(final WebSocketContext context) throws IOException
        {
            context.onClose(1001, "OK");
        }

        @Override
        public void onDisconnect(final WebSocketContext context) throws IOException
        {

        }

        @Override
        public void onTimeout(final WebSocketContext context) throws IOException
        {

        }
    }
}

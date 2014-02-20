//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.benchmark;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServlet;

public class WebSocketServerTest8
{
    private static final Logger logger = Log.getLogger(WebSocketServerTest8.class);

    public static void main(String... args) throws Exception
    {
        Server server = new Server();
        Connector connector = new SelectChannelConnector();
        connector.setPort(8080);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(server, "/", true, false);
        ServletHolder servletHolder = new ServletHolder(ServerWebSocket.class);
        context.addServlet(servletHolder, "/*");

        server.start();
        server.join();

    }

    public static class ServerWebSocket extends WebSocketServlet
    {
        @Override
        public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol)
        {
            return new Target();
        }

        private static class Target implements WebSocket.OnTextMessage
        {
            private Connection connection;
            private int iterations=1;

            @Override
            public void onOpen(Connection connection)
            {
                this.connection = connection;
            }

            @Override
            public void onMessage(String data)
            {
                // System.err.println("onMessage: "+data);
                try
                {
                    if (data.startsWith("echos="))
                        iterations=Integer.parseInt(data.substring(6));
                    else
                    {
                        for (int i=0;i<iterations;i++)
                            connection.sendMessage(data);
                    }
                }
                catch (IOException x)
                {
                    connection.close(1011, x.getMessage());
                }
            }

            @Override
            public void onClose(int closeCode, String message)
            {
                logger.debug("WebSocket closed {}/{}", closeCode, message);
            }
        }
    }
}

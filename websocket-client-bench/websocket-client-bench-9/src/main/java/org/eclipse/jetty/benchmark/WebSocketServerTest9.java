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

import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeFilter;
import org.eclipse.jetty.websocket.server.pathmap.ServletPathSpec;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;

public class WebSocketServerTest9
{
    private static final Logger logger = Log.getLogger(WebSocketServerTest9.class);

    public static void main(String... args) throws Exception
    {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(8080);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(server, "/", true, false);
        WebSocketUpgradeFilter filter = WebSocketUpgradeFilter.configureContext(context);
        filter.addMapping(new ServletPathSpec("/*"), new WebSocketCreator()
        {
            @Override
            public Object createWebSocket(ServletUpgradeRequest servletUpgradeRequest, ServletUpgradeResponse servletUpgradeResponse)
            {
                return new ServerWebSocket();
            }
        });

        // TODO: Dummy servlet otherwise the filter does not work, *and* must NOT be mapped to /*
        context.addServlet(HttpServlet.class, "/");

        server.start();
        server.join();
    }

    public static class ServerWebSocket implements WebSocketListener
    {
        private Session session;
        private int iterations=1;

        @Override
        public void onWebSocketConnect(Session session)
        {
            this.session = session;
        }

        @Override
        public void onWebSocketBinary(byte[] bytes, int i, int i2)
        {
        }
        
        @Override
        public void onWebSocketText(String data)
        {
            // System.err.println("onMessage: "+data);
            try
            {
                if (data.startsWith("echos="))
                    iterations=Integer.parseInt(data.substring(6));
                else
                {
                    for (int i=0;i<iterations;i++)
                        session.getRemote().sendString(data);
                }
            }
            catch (IOException x)
            {
                session.close(1011, x.getMessage());
            }
        }

        @Override
        public void onWebSocketClose(int closeCode, String message)
        {
            logger.debug("WebSocket closed {}/{}", closeCode, message);
        }

        @Override
        public void onWebSocketError(Throwable throwable)
        {
            logger.warn("WebSocket error", throwable);
        }
    }
}

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
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeFilter;
import org.eclipse.jetty.websocket.server.pathmap.ServletPathSpec;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WebSocketClient9SerialThroughputTest
{
    private static final Logger logger = Log.getLogger(WebSocketClient9SerialThroughputTest.class);
    private Server server;
    private NetworkConnector connector;
    private WebSocketClient client;

    @Before
    public void start() throws Exception
    {
        server = new Server();
        connector = new ServerConnector(server);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(server, "", true, false);
        WebSocketUpgradeFilter filter = WebSocketUpgradeFilter.configureContext(context);
        filter.addMapping(new ServletPathSpec("/ws"), new WebSocketCreator()
        {
            @Override
            public Object createWebSocket(ServletUpgradeRequest servletUpgradeRequest, ServletUpgradeResponse servletUpgradeResponse)
            {
                return new ServerWebSocket();
            }
        });

        // TODO: Dummy servlet otherwise the filter does not work, *and* must NOT be mapped to /*
        context.addServlet(HttpServlet.class, "/ws");

        server.start();

        QueuedThreadPool executor = new QueuedThreadPool();
        executor.setName(executor.getName() + "-client");
        client = new WebSocketClient();
        client.setExecutor(executor);
        client.start();
    }

    @After
    public void dispose() throws Exception
    {
        Thread.sleep(1000);
        if (server != null)
            server.stop();
        if (client != null)
            client.stop();
    }

    @Test
    public void testIterative() throws Exception
    {
        Object webSocket = new ClientWebSocket();
        final Session session = client.connect(webSocket, new URI("ws://localhost:" + connector.getLocalPort() + "/ws"))
                .get(5, TimeUnit.SECONDS);

        // At least 25k requests to warmup properly (use -XX:+PrintCompilation to verify JIT activity)
        int runs = 3;
        int iterations = 200_000;
        for (int i = 0; i < runs; ++i)
        {
            run(session, iterations);
        }

        // Re-run after warmup
        iterations = 1_000_000;
        for (int i = 0; i < runs; ++i)
        {
            run(session, iterations);
        }
    }

    private void run(Session session, int iterations) throws IOException
    {
        char[] chars = new char[1024];
        Arrays.fill(chars, 'x');
        String message = new String(chars);

        long begin = System.nanoTime();
        for (int i = 0; i < iterations; ++i)
        {
            test(session, message);
        }
        long end = System.nanoTime();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(end - begin);
        logger.info("{} messages in {} ms, {} msgs/s", iterations, elapsed, elapsed > 0 ? iterations * 1000 / elapsed : -1);
    }

    private void test(Session session, String message)
    {
        try
        {
            session.getRemote().sendString(message);
        }
        catch (IOException x)
        {
            session.close(1011, x.getMessage());
        }
    }

    public static class ClientWebSocket implements WebSocketListener
    {
        @Override
        public void onWebSocketConnect(Session session)
        {
        }

        @Override
        public void onWebSocketText(String message)
        {
        }

        @Override
        public void onWebSocketBinary(byte[] bytes, int offset, int length)
        {
        }

        @Override
        public void onWebSocketClose(int code, String reason)
        {
            logger.warn("WebSocket closed {}/{}", code, reason);
        }

        @Override
        public void onWebSocketError(Throwable throwable)
        {
            logger.warn("WebSocket error", throwable);
        }
    }

    private static class ServerWebSocket implements WebSocketListener
    {
        private Session session;

        @Override
        public void onWebSocketConnect(Session session)
        {
            this.session = session;
        }

        @Override
        public void onWebSocketText(String message)
        {
            try
            {
                session.getRemote().sendString(message);
            }
            catch (IOException x)
            {
                session.close(1011, x.getMessage());
            }
        }

        @Override
        public void onWebSocketBinary(byte[] bytes, int i, int i2)
        {
        }

        @Override
        public void onWebSocketClose(int code, String reason)
        {
            logger.warn("WebSocket closed {}/{}", code, reason);
        }

        @Override
        public void onWebSocketError(Throwable throwable)
        {
            logger.warn("WebSocket error", throwable);
        }
    }
}

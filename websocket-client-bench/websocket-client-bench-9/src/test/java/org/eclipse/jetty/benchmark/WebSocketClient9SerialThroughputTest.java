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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.component.Dumpable;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WebSocketClient9SerialThroughputTest
{
    private static final int MAX_DIGITS = 9;
    private static final Logger logger = Log.getLogger(WebSocketClient9SerialThroughputTest.class);

    private Server server;
    private NetworkConnector connector;
    private WebSocketClient wsClient;

    @Before
    public void start() throws Exception
    {
        server = new Server();
        connector = new ServerConnector(server);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(server, "", true, false);
        WebSocketUpgradeFilter filter = WebSocketUpgradeFilter.configureContext(context);
        filter.addMapping(new ServletPathSpec("/"), new WebSocketCreator()
        {
            @Override
            public Object createWebSocket(ServletUpgradeRequest servletUpgradeRequest, ServletUpgradeResponse servletUpgradeResponse)
            {
                return new ServerWebSocket();
            }
        });

        // TODO: Dummy servlet otherwise the filter does not work, *and* must NOT be mapped to /*
//        context.addServlet(HttpServlet.class, "/ws");

        server.start();

        QueuedThreadPool executor = new QueuedThreadPool();
        executor.setName(executor.getName() + "-client");
        wsClient = new WebSocketClient();
        wsClient.setExecutor(executor);
        wsClient.start();
    }

    @After
    public void dispose() throws Exception
    {
        if (wsClient != null)
            wsClient.stop();
        if (server != null)
            server.stop();
    }

    @Test
    public void testIterative() throws Exception
    {
        int runs = 10;
        int iterations = 50_000;
        int count = runs * iterations;
        CountDownLatch latch = new CountDownLatch(count);
        ClientWebSocket client = new ClientWebSocket(latch);
        wsClient.setMaxIdleTimeout(30000);

        try (Session session = wsClient.connect(client, new URI("ws://localhost:" + connector.getLocalPort()))
                .get(5, TimeUnit.SECONDS))
        {
            for (int i = 0; i < runs; ++i)
            {
                run(session, i, iterations);
            }

            boolean complete = latch.await(5 * count, TimeUnit.MILLISECONDS);
            if (!complete)
            {
                logger.info(((Dumpable)client).dump());
                logger.info(server.dump());
                logger.info("Server Arrived/Expected: {}/{}", ServerWebSocket.counter.get(), count);
                logger.info("Client Arrived/Expected: {}/{}", count - latch.getCount(), count);
                Assert.fail();
            }
        }
    }

    private void run(Session session, int currentRun, int iterations) throws IOException
    {
        char[] chars = new char[1024];
        Arrays.fill(chars, 'x');
        long begin = System.nanoTime();
        perform(session, chars, currentRun, iterations);
        long end = System.nanoTime();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(end - begin);
        logger.info("{} messages in {} ms, {} msgs/s", iterations, elapsed, elapsed > 0 ? iterations * 1000 / elapsed : -1);
    }

    protected void perform(Session session, char[] chars, int currentRun, int iterations) throws IOException
    {
        for (int i = 0; i < iterations; ++i)
        {
            String number = String.valueOf(currentRun * iterations + i);
            String messageNumber = number;
            for (int j = 0; j < (MAX_DIGITS - number.length()); ++j)
                messageNumber = "0" + messageNumber;
            for (int j = 0; j < MAX_DIGITS; ++j)
                chars[j] = messageNumber.charAt(j);
            test(session, new String(chars));
        }
    }

    private void test(Session session, String message) throws IOException
    {
        session.getRemote().sendString(message);
    }

    public static class ClientWebSocket implements WebSocketListener
    {
        private final CountDownLatch latch;

        public ClientWebSocket(CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        public void onWebSocketConnect(Session session)
        {
        }

        @Override
        public void onWebSocketText(String message)
        {
            latch.countDown();
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

    public static class ServerWebSocket implements WebSocketListener
    {
        private static final AtomicInteger counter = new AtomicInteger();
        private Session session;

        @Override
        public void onWebSocketConnect(Session session)
        {
            this.session = session;
            counter.set(0);
        }

        @Override
        public void onWebSocketText(String message)
        {
                try
                {
                    int actual = Integer.parseInt(message.substring(0, MAX_DIGITS));
                    int expected = counter.getAndIncrement();
                    if (actual != expected)
                    {
                        logger.info("MISMATCH: actual {} != expected {}", actual, expected);
                        throw new IllegalStateException();
                    }

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

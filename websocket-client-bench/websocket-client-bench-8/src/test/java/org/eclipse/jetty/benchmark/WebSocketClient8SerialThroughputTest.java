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
import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketClient;
import org.eclipse.jetty.websocket.WebSocketClientFactory;
import org.eclipse.jetty.websocket.WebSocketServlet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class WebSocketClient8SerialThroughputTest
{
    private static final int MAX_DIGITS = 9;
    private static final Logger logger = Log.getLogger(WebSocketClient8SerialThroughputTest.class);

    private Server server;
    private Connector connector;
    private WebSocketClientFactory clientFactory;

    @Before
    public void start() throws Exception
    {
        server = new Server();
        connector = new SelectChannelConnector();
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(server, "/", true, false);
        ServletHolder servletHolder = new ServletHolder(ServerWebSocket.class);
        context.addServlet(servletHolder, "/*");

        server.start();

        QueuedThreadPool executor = new QueuedThreadPool();
        executor.setName(executor.getName() + "-client");
        clientFactory = new WebSocketClientFactory(executor);
        clientFactory.start();
    }

    @After
    public void dispose() throws Exception
    {
        if (clientFactory != null)
            clientFactory.stop();
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
        WebSocketClient webSocketClient = clientFactory.newWebSocketClient();
        webSocketClient.setMaxIdleTime(30000);

        WebSocket.Connection connection = webSocketClient.open(new URI("ws://localhost:" + connector.getLocalPort()), client)
                .get(5, TimeUnit.SECONDS);
        try
        {
            for (int i = 0; i < runs; ++i)
            {
                run(connection, i, iterations);
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
        finally
        {
            connection.close();
        }
    }

    private void run(WebSocket.Connection session, int currentRun, int iterations) throws Exception
    {
        char[] chars = new char[1024];
        Arrays.fill(chars, 'x');
        long begin = System.nanoTime();
        perform(session, chars, currentRun, iterations);
        long end = System.nanoTime();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(end - begin);
        logger.info("{} messages in {} ms, {} msgs/s", iterations, elapsed, elapsed > 0 ? iterations * 1000 / elapsed : -1);
    }

    protected void perform(WebSocket.Connection session, char[] chars, int currentRun, int iterations) throws IOException
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

    private void test(WebSocket.Connection connection, String message) throws IOException
    {
        connection.sendMessage(message);
    }

    public static class ClientWebSocket implements WebSocket.OnTextMessage
    {
        private final CountDownLatch latch;

        public ClientWebSocket(CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        public void onOpen(Connection connection)
        {
        }

        @Override
        public void onMessage(String data)
        {
            latch.countDown();
        }

        @Override
        public void onClose(int closeCode, String message)
        {
            logger.warn("WebSocket closed {}/{}", closeCode, message);
        }
    }

    public static class ServerWebSocket extends WebSocketServlet
    {
        private static final AtomicInteger counter = new AtomicInteger();

        @Override
        public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol)
        {
            return new Target();
        }

        private static class Target implements WebSocket.OnTextMessage
        {
            private Connection connection;

            @Override
            public void onOpen(Connection connection)
            {
                this.connection = connection;
                counter.set(0);
            }

            @Override
            public void onMessage(String message)
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

                    connection.sendMessage(message);
                }
                catch (IOException x)
                {
                    connection.close(1011, x.getMessage());
                }
            }

            @Override
            public void onClose(int closeCode, String message)
            {
                logger.warn("WebSocket closed {}/{}", closeCode, message);
            }
        }
    }
}

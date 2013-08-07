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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
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
    private static final Logger logger = Log.getLogger(WebSocketClient8SerialThroughputTest.class);

    private final AtomicLong messages = new AtomicLong();
    private Server server;
    private Connector connector;
    private WebSocketClientFactory clientFactory;
    private Timer scheduler;

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

        scheduler = new Timer();
    }

    @After
    public void dispose() throws Exception
    {
        if (scheduler != null)
            scheduler.cancel();
        if (clientFactory != null)
            clientFactory.stop();
        if (server != null)
            server.stop();
    }

    @Test
    public void testIterative() throws Exception
    {
        ClientWebSocket webSocket = new ClientWebSocket();
        WebSocketClient webSocketClient = clientFactory.newWebSocketClient();
        webSocketClient.setMaxIdleTime(30000);
        WebSocket.Connection connection = webSocketClient.open(new URI("ws://localhost:" + connector.getLocalPort()), webSocket)
                .get(5, TimeUnit.SECONDS);

        // At least 25k requests to warmup properly (use -XX:+PrintCompilation to verify JIT activity)
        int runs = 5;
        int iterations = 20_000;
        for (int i = 0; i < runs; ++i)
        {
            run(webSocket, connection, iterations);
        }

        // Re-run after warmup
        iterations = 200_000;
        for (int i = 0; i < runs; ++i)
        {
            run(webSocket, connection, iterations);
        }
    }

    private void run(ClientWebSocket webSocket, WebSocket.Connection connection, int iterations) throws Exception
    {
        char[] chars = new char[1024];
        Arrays.fill(chars, 'x');
        String message = new String(chars);

        webSocket.reset(iterations);

        long begin = System.nanoTime();
        for (int i = 0; i < iterations; ++i)
        {
            test(connection, message);
        }
        long end = System.nanoTime();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(end - begin);
        logger.info("{} messages in {} ms, {} msgs/s", iterations, elapsed, elapsed > 0 ? iterations * 1000 / elapsed : -1);

        Assert.assertTrue(webSocket.await(iterations, TimeUnit.MILLISECONDS));
        messages.set(0);
    }

    private void test(WebSocket.Connection connection, String message) throws Exception
    {
        TimerTask task = new TimerTask()
        {
            @Override
            public void run()
            {
                System.err.println("Messages: " + messages);
                clientFactory.dumpStdErr();
                server.dumpStdErr();
            }
        };
        scheduler.schedule(task, 5000);
        messages.incrementAndGet();
        connection.sendMessage(message);
        task.cancel();
    }

    public class ClientWebSocket implements WebSocket.OnTextMessage
    {
        private final AtomicReference<CountDownLatch> latch = new AtomicReference<>();

        @Override
        public void onOpen(Connection connection)
        {
        }

        @Override
        public void onMessage(String data)
        {
            messages.decrementAndGet();
            latch.get().countDown();
        }

        @Override
        public void onClose(int closeCode, String message)
        {
            logger.warn("WebSocket closed {}/{}", closeCode, message);
        }

        private void reset(int count)
        {
            latch.set(new CountDownLatch(count));
        }

        private boolean await(long timeout, TimeUnit unit) throws InterruptedException
        {
            return latch.get().await(timeout, unit);
        }
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

            @Override
            public void onOpen(Connection connection)
            {
                this.connection = connection;
            }

            @Override
            public void onMessage(String data)
            {
                try
                {
                    connection.sendMessage(data);
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

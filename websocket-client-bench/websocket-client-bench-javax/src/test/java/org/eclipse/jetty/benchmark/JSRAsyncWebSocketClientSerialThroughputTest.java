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
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

import junit.framework.Assert;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JSRAsyncWebSocketClientSerialThroughputTest
{
    private static final int MAX_DIGITS = 9;
    protected static final Logger logger = Log.getLogger(JSRAsyncWebSocketClientSerialThroughputTest.class);

    private Server server;
    private NetworkConnector connector;
    private WebSocketContainer client;

    @Before
    public void start() throws Exception
    {
        server = new Server();
        connector = new ServerConnector(server);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(server, "/", true, false);
        ServerContainer container = WebSocketServerContainerInitializer.configureContext(context);
        ServerEndpointConfig config = ServerEndpointConfig.Builder.create(websocketServerClass(), "/")
//                .extensions(Arrays.<Extension>asList(new JsrExtension("permessage-deflate")))
                .build();
        container.addEndpoint(config);

        server.start();

        client = ContainerProvider.getWebSocketContainer();
        server.addBean(client, true);
    }

    @After
    public void dispose() throws Exception
    {
        if (server != null)
            server.stop();
    }

    protected Class<? extends Endpoint> websocketServerClass()
    {
        return ServerWebSocket.class;
    }

    protected ClientEndpointConfig newClientConfiguration()
    {
        return ClientEndpointConfig.Builder.create()
//                .extensions(Arrays.<Extension>asList(new JsrExtension("permessage-deflate")))
                .build();
    }

    protected void configureClientSession(Session session) throws IOException
    {
    }

    @Test
    public void testIterative() throws Exception
    {
        int runs = 10;
        int iterations = 50_000;
        int count = runs * iterations;
        CountDownLatch latch = new CountDownLatch(count);
        ClientWebSocket webSocket = new ClientWebSocket(latch);
        URI uri = new URI("ws://localhost:" + connector.getLocalPort());
        try (Session session = client.connectToServer(webSocket, newClientConfiguration() , uri))
        {
            configureClientSession(session);

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

    private void run(final Session session, final int currentRun, final int iterations) throws IOException
    {
        final char[] chars = new char[1024];
        Arrays.fill(chars, 'x');

        SendHandler handler = new SendHandler()
        {
            private int iteration;

            @Override
            public void onResult(SendResult result)
            {
                while (iteration < iterations)
                {
                    String number = String.valueOf(currentRun * iterations + iteration);
                    ++iteration;
                    String messageNumber = number;
                    for (int j = 0; j < (MAX_DIGITS - number.length()); ++j)
                        messageNumber = "0" + messageNumber;
                    for (int j = 0; j < MAX_DIGITS; ++j)
                        chars[j] = messageNumber.charAt(j);
                    test(session, new String(chars), this);
                }
            }
        };

        long begin = System.nanoTime();
        handler.onResult(new SendResult());
        long end = System.nanoTime();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(end - begin);
        logger.info("{} messages in {} ms, {} msgs/s", iterations, elapsed, elapsed > 0 ? iterations * 1000 / elapsed : -1);
    }

    protected void test(Session session, String message, SendHandler callback)
    {
        session.getAsyncRemote().sendText(message, callback);
    }

    protected static void close(Session session, Throwable x)
    {
        try
        {
            session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, x.getMessage()));
        }
        catch (IOException xx)
        {
            xx.printStackTrace();
        }
    }

    public static class ClientWebSocket extends Endpoint implements MessageHandler.Whole<String>
    {
        private final CountDownLatch latch;

        public ClientWebSocket(CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        public void onOpen(Session session, EndpointConfig config)
        {
            session.addMessageHandler(this);
        }

        @Override
        public void onMessage(String message)
        {
            latch.countDown();
        }

        @Override
        public void onError(Session session, Throwable thr)
        {
            logger.warn("Client Error", thr);
        }
    }

    public static class ServerWebSocket extends Endpoint implements MessageHandler.Whole<String>, SendHandler
    {
        private static final AtomicInteger counter = new AtomicInteger();
        protected Session session;

        @Override
        public void onOpen(Session session, EndpointConfig config)
        {
            this.session = session;
            session.addMessageHandler(this);
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

                session.getAsyncRemote().sendText(message, this);
            }
            catch (Exception x)
            {
                close(session, x);
            }
        }

        @Override
        public void onResult(SendResult result)
        {
        }
    }
}

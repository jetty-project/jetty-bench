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

import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.component.Dumpable;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class WebSocketClientSerialThroughputTest
{
    private static final Logger logger = Log.getLogger(WebSocketClientSerialThroughputTest.class);
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
        ServerEndpointConfig config = ServerEndpointConfig.Builder.create(ServerWebSocket.class, "/").build();
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

//    @Test
    public void testIterativeNoBatching() throws Exception
    {
        testIterative(false);
    }

    @Test
    public void testIterativeBatching() throws Exception
    {
        testIterative(true);
    }

    private void testIterative(boolean batching) throws Exception
    {
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
//                .extensions(Arrays.<Extension>asList(new JsrExtension("deflate-frame")))
                .build();

        int runs = 1;
        int iterations = 5_000;
        int count = runs * iterations;
        CountDownLatch latch = new CountDownLatch(count);
        ClientWebSocket webSocket = new ClientWebSocket(latch);
        URI uri = new URI("ws://localhost:" + connector.getLocalPort());
        try (Session session = client.connectToServer(webSocket, config, uri))
        {
            if (batching)
                session.getBasicRemote().setBatchingAllowed(true);

            for (int i = 0; i < runs; ++i)
            {
                run(session, iterations, batching);
            }

            boolean complete = latch.await(5, TimeUnit.SECONDS);
            if (!complete)
            {
                logger.info(((Dumpable)client).dump());
                logger.info(server.dump());
                logger.info("Arrived/Expected: {}/{}", count - latch.getCount(), count);
            }
            Assert.assertTrue(latch.await(count, TimeUnit.MILLISECONDS));
        }
    }

    private void run(Session session, int iterations, boolean batching) throws IOException
    {
        char[] chars = new char[1024];
        Arrays.fill(chars, 'x');
        String message = new String(chars);

        long begin = System.nanoTime();
        for (int i = 0; i < iterations; ++i)
        {
            test(session, message);
        }
        if (batching)
            session.getBasicRemote().flushBatch();
        long end = System.nanoTime();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(end - begin);
        logger.info("{} messages in {} ms, {} msgs/s", iterations, elapsed, elapsed > 0 ? iterations * 1000 / elapsed : -1);
    }

    private void test(Session session, String message)
    {
        try
        {
            session.getBasicRemote().sendText(message);
        }
        catch (IOException x)
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
    }

    public static class ServerWebSocket extends Endpoint implements MessageHandler.Whole<String>, SendHandler
    {
        private Session session;

        @Override
        public void onOpen(Session session, EndpointConfig config)
        {
            this.session = session;
            session.addMessageHandler(this);
        }

        @Override
        public void onMessage(String message)
        {
            try
            {
//                session.getBasicRemote().sendText(message);
                session.getAsyncRemote().sendText(message, this);
            }
            catch (Exception x)
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
        }

        @Override
        public void onResult(SendResult result)
        {
        }
    }
}

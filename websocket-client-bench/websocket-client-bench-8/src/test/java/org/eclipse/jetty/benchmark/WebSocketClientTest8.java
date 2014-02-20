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

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketClient;
import org.eclipse.jetty.websocket.WebSocketClientFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class WebSocketClientTest8
{
    private static final Logger logger = Log.getLogger(WebSocketClientTest8.class);

    private static WebSocketClientFactory clientFactory;

    @BeforeClass
    public static void start() throws Exception
    {
        QueuedThreadPool executor = new QueuedThreadPool();
        executor.setName(executor.getName() + "-client");
        clientFactory = new WebSocketClientFactory(executor);
        clientFactory.start();

        run(1,1,1,1,1);
        run(100,10,100,100,100);
        run(100,10,100,100,100);
        run(100,10,100,100,100);
        run(100,10,100,10,2048);
        run(100,10,100,10,2048);
        run(100,10,100,10,2048);
    }

    @AfterClass
    public static void dispose() throws Exception
    {
        if (clientFactory != null)
            clientFactory.stop();
    }
    
    @Test
    public void testWarmup() throws Exception
    {
    }
    
    @Test
    public void testServerToClientFlatOutSmall() throws Exception
    {
        run(10,0,10,100000,100);
    }
    
    @Test
    public void testServerToClientPausesSmall() throws Exception
    {
        run(1000,10,10,1000,100);
    }

    @Test
    public void testServerToClientFlatOutLarge() throws Exception
    {
        run(10,0,10,10000,2048);
    }
    
    @Test
    public void testServerToClientPausesLarge() throws Exception
    {
        run(1000,10,10,100,2048);
    }

    @Test
    public void testClientToServerFlatOutSmall() throws Exception
    {
        run(10,0,1000000,0,100);
    }

    @Test
    public void testClientToServerPauseSmall() throws Exception
    {
        run(1000,10,10000,0,100);
    }
    @Test
    public void testClientToServerFlatOutLarge() throws Exception
    {
        run(10,0,100000,0,2048);
    }

    @Test
    public void testClientToServerPauseLarge() throws Exception
    {
        run(1000,10,1000,0,2048);
    }
    
    
    
    
    
    private static void run(int iterations, int pauseMS, int sends, int echos, int size) throws Exception
    {
        ClientWebSocket webSocket = new ClientWebSocket();
        WebSocketClient webSocketClient = clientFactory.newWebSocketClient();
        webSocketClient.setMaxIdleTime(30000);
        WebSocket.Connection connection = webSocketClient.open(new URI("ws://localhost:8080"), webSocket)
                .get(5, TimeUnit.SECONDS);
        
        try
        {
            long begin = System.nanoTime();

            for (int i=0;i<iterations;i++)
            {
                run(webSocket,connection,sends,echos,size);
                if (pauseMS>0)
                    Thread.sleep(pauseMS);
            }

            long end = System.nanoTime();
            long elapsed = TimeUnit.NANOSECONDS.toMillis(end - begin);
            logger.info("iterations={}, pause={}ms, sends={} echos={} size={}: sent={} recv={} in {} ms", 
                iterations,pauseMS,sends, echos, size, iterations*sends,iterations*sends*echos, elapsed-(iterations*pauseMS));
        }
        finally
        {
            connection.close();
        }
    }
    
    
    private static void run(ClientWebSocket webSocket, WebSocket.Connection connection, int sends, int echos, int size) throws Exception
    {
        char[] chars = new char[size];
        Arrays.fill(chars, 'x');
        String message = new String(chars);

        CountDownLatch latch = new CountDownLatch(sends*echos+1);
        webSocket.latch.set(latch);
        connection.sendMessage("echos="+echos);
        for (int i = 0; i < sends; ++i)
            connection.sendMessage(message);
        connection.sendMessage("echos=1");
        connection.sendMessage("end");
        latch.await();
    }


    public static class ClientWebSocket implements WebSocket.OnTextMessage
    {
        private final AtomicReference<CountDownLatch> latch = new AtomicReference<>();

        @Override
        public void onOpen(Connection connection)
        {
        }

        @Override
        public void onMessage(String data)
        {
            // System.err.println("received: "+data);
            latch.get().countDown();
        }

        @Override
        public void onClose(int closeCode, String message)
        {
            logger.debug("WebSocket closed {}/{}", closeCode, message);
        }


    }

}

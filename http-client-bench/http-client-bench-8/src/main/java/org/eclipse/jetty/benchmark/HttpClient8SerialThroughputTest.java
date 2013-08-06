package org.eclipse.jetty.benchmark;//
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.Address;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class HttpClient8SerialThroughputTest
{
    private final Logger logger = Log.getLogger(HttpClient8SerialThroughputTest.class);

    protected String scheme;
    protected Server server;
    protected HttpClient client;
    protected Connector connector;

    public void start(Handler handler) throws Exception
    {
        if (server == null)
            server = new Server();
        connector = new SelectChannelConnector();
        server.addConnector(connector);
        server.setHandler(handler);
        server.start();

        QueuedThreadPool executor = new QueuedThreadPool();
        executor.setName(executor.getName() + "-client");
        client = new HttpClient();
        client.setThreadPool(executor);
        client.start();
    }

    @After
    public void dispose() throws Exception
    {
        if (client != null)
            client.stop();
        if (server != null)
            server.stop();
        server = null;
    }

    @Test
    public void testIterative() throws Exception
    {
        start(new LoadHandler());

        client.setMaxConnectionsPerAddress(32768);
        client.setMaxQueueSizePerAddress(1024 * 1024);

        Random random = new Random();
        // At least 25k requests to warmup properly (use -XX:+PrintCompilation to verify JIT activity)
        int runs = 5;
        int iterations = 5000;
        for (int i = 0; i < runs; ++i)
        {
            run(random, iterations);
        }

        // Re-run after warmup
        iterations = 50_000;
        for (int i = 0; i < runs; ++i)
        {
            run(random, iterations);
        }
    }

    private void run(Random random, int iterations) throws Exception
    {
        CountDownLatch latch = new CountDownLatch(iterations);
        List<String> failures = new ArrayList<>();

        int factor = logger.isDebugEnabled() ? 25 : 1;
        factor *= "http".equalsIgnoreCase(scheme) ? 10 : 1000;

        // Dumps the state of the client if the test takes too long
/*
        final Thread testThread = Thread.currentThread();
        Scheduler.Task task = client.getScheduler().schedule(new Runnable()
        {
            @Override
            public void run()
            {
                logger.warn("Interrupting test, it is taking too long");
                for (String host : Arrays.asList("localhost", "127.0.0.1"))
                {
                    HttpDestinationOverHTTP destination = (HttpDestinationOverHTTP)client.getDestination(scheme, host, connector.getLocalPort());
                    HttpConnectionPool connectionPool = destination.getHttpConnectionPool();
                    for (Connection connection : new ArrayList<>(connectionPool.getActiveConnections()))
                    {
                        HttpConnectionOverHTTP active = (HttpConnectionOverHTTP)connection;
                        logger.warn(active.getEndPoint() + " exchange " + active.getHttpChannel().getHttpExchange());
                    }
                }
                testThread.interrupt();
            }
        }, iterations * factor, TimeUnit.MILLISECONDS);
*/

        long begin = System.nanoTime();
        for (int i = 0; i < iterations; ++i)
        {
//            test(random, latch, failures);
            test("http", "localhost", "GET", false, false, 64 * 1024, false, latch, failures);
        }
        Assert.assertTrue(latch.await(iterations, TimeUnit.SECONDS));
        long end = System.nanoTime();
//        task.cancel();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(end - begin);
        logger.info("{} requests in {} ms, {} req/s", iterations, elapsed, elapsed > 0 ? iterations * 1000 / elapsed : -1);

        for (String failure : failures)
            System.err.println("FAILED: "+failure);

        Assert.assertTrue(failures.toString(), failures.isEmpty());
    }

    private void test(Random random, final CountDownLatch latch, final List<String> failures) throws Exception
    {
        // Choose a random destination
        String host = random.nextBoolean() ? "localhost" : "127.0.0.1";
        // Choose a random method
        String method = random.nextBoolean() ? "GET" : "POST";

        boolean ssl = false;

        // Choose randomly whether to close the connection on the client or on the server
        boolean clientClose = false;
        if (!ssl && random.nextBoolean())
            clientClose = true;
        boolean serverClose = false;
        if (!ssl && random.nextBoolean())
            serverClose = true;

        int maxContentLength = 64 * 1024;
        int contentLength = random.nextInt(maxContentLength) + 1;

        test(scheme, host, method, clientClose, serverClose, contentLength, true, latch, failures);
    }

    private void test(String scheme, String host, String method, boolean clientClose, boolean serverClose, int contentLength, final boolean checkContentLength, final CountDownLatch latch, final List<String> failures) throws Exception
    {
        ContentExchange exchange = new ContentExchange(true)
        {
            private final AtomicInteger contentLength = new AtomicInteger();

            @Override
            protected void onResponseHeaderComplete() throws IOException
            {
                if (checkContentLength)
                {
                    String content = getResponseFields().getStringField("X-Content");
                    if (content != null)
                        contentLength.set(Integer.parseInt(content));
                }
            }

            @Override
            protected void onResponseContent(Buffer content) throws IOException
            {
                if (checkContentLength)
                    contentLength.addAndGet(-content.length());
            }

            @Override
            protected void onResponseComplete() throws IOException
            {
                if (checkContentLength && contentLength.get() != 0)
                    failures.add("Content length mismatch " + contentLength);
                latch.countDown();
            }

            @Override
            protected void onException(Throwable x)
            {
                failures.add("Result failed " + x);
                latch.countDown();
            }
        };
        exchange.setAddress(new Address(host, connector.getLocalPort()));
        exchange.setScheme(scheme);
        exchange.setMethod(method);
        exchange.setRequestURI("/");

        if (clientClose)
            exchange.setRequestHeader(HttpHeaders.CONNECTION, "close");
        else if (serverClose)
            exchange.setRequestHeader("X-Close", "true");

        switch (method)
        {
            case "GET":
                exchange.setRequestHeader("X-Download", String.valueOf(contentLength));
                break;
            case "POST":
                exchange.setRequestHeader("X-Upload", String.valueOf(contentLength));
                exchange.setRequestContent(new ByteArrayBuffer((new byte[contentLength])));
                break;
        }

        client.send(exchange);
        exchange.waitForDone();
    }

    private class LoadHandler extends AbstractHandler
    {
        @Override
        public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
        {
            String method = request.getMethod().toUpperCase(Locale.ENGLISH);
            switch (method)
            {
                case "GET":
                    int contentLength = request.getIntHeader("X-Download");
                    if (contentLength > 0)
                    {
                        response.setHeader("X-Content", String.valueOf(contentLength));
                        response.getOutputStream().write(new byte[contentLength]);
                    }
                    break;
                case "POST":
                    response.setHeader("X-Content", request.getHeader("X-Upload"));
                    IO.copy(request.getInputStream(), response.getOutputStream());
                    break;
            }

            if (Boolean.parseBoolean(request.getHeader("X-Close")))
                response.setHeader("Connection", "close");

            baseRequest.setHandled(true);
        }
    }
}

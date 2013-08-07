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
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class HttpClient9SerialThroughputTest
{
    private final Logger logger = Log.getLogger(HttpClient9SerialThroughputTest.class);
    private Server server;
    private NetworkConnector connector;
    private HttpClient client;

    public void start(Handler handler) throws Exception
    {
        server = new Server();
        connector = new ServerConnector(server);
        server.addConnector(connector);
        server.setHandler(handler);
        server.start();

        QueuedThreadPool executor = new QueuedThreadPool();
        executor.setName(executor.getName() + "-client");
        client = new HttpClient();
        client.setExecutor(executor);
        client.start();
    }

    @After
    public void dispose() throws Exception
    {
        if (client != null)
            client.stop();
        if (server != null)
            server.stop();
    }

    @Test
    public void testIterative() throws Exception
    {
        start(new LoadHandler());

        client.setMaxConnectionsPerDestination(32768);
        client.setMaxRequestsQueuedPerDestination(1024 * 1024);
        client.setDispatchIO(false);
        client.setStrictEventOrdering(false);

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

    private void run(Random random, int iterations) throws InterruptedException
    {
        CountDownLatch latch = new CountDownLatch(iterations);
        List<String> failures = new ArrayList<>();

        long begin = System.nanoTime();
        for (int i = 0; i < iterations; ++i)
        {
//            test(random, latch, failures);
            test("http", "localhost", "GET", false, false, 64 * 1024, latch, failures);
        }
        Assert.assertTrue(latch.await(iterations, TimeUnit.SECONDS));
        long end = System.nanoTime();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(end - begin);
        logger.info("{} requests in {} ms, {} req/s", iterations, elapsed, elapsed > 0 ? iterations * 1000 / elapsed : -1);

        for (String failure : failures)
            System.err.println("FAILED: "+failure);

        Assert.assertTrue(failures.toString(), failures.isEmpty());
    }

    private void test(Random random, final CountDownLatch latch, final List<String> failures) throws InterruptedException
    {
        // Choose a random destination
        String host = random.nextBoolean() ? "localhost" : "127.0.0.1";
        // Choose a random method
        HttpMethod method = random.nextBoolean() ? HttpMethod.GET : HttpMethod.POST;

        // Choose randomly whether to close the connection on the client or on the server
        boolean clientClose = false;
        if (random.nextBoolean())
            clientClose = true;
        boolean serverClose = false;
        if (random.nextBoolean())
            serverClose = true;

        int maxContentLength = 64 * 1024;
        int contentLength = random.nextInt(maxContentLength) + 1;

        test("http", host, method.asString(), clientClose, serverClose, contentLength, latch, failures);
    }

    private void test(String scheme, String host, String method, boolean clientClose, boolean serverClose, int contentLength, final CountDownLatch latch, final List<String> failures) throws InterruptedException
    {
        Request request = client.newRequest(host, connector.getLocalPort())
                .scheme(scheme)
                .method(method);

        if (clientClose)
            request.header(HttpHeader.CONNECTION, "close");
        else if (serverClose)
            request.header("X-Close", "true");

        switch (method)
        {
            case "GET":
                request.header("X-Download", String.valueOf(contentLength));
                break;
            case "POST":
                request.header("X-Upload", String.valueOf(contentLength));
                request.content(new BytesContentProvider(new byte[contentLength]));
                break;
        }

        final CountDownLatch requestLatch = new CountDownLatch(1);
        request.send(new Response.Listener.Empty()
        {
            @Override
            public void onComplete(Result result)
            {
                if (result.isFailed())
                {
                    result.getFailure().printStackTrace();
                    failures.add("Result failed " + result);
                }

                requestLatch.countDown();
                latch.countDown();
            }
        });
        requestLatch.await(5, TimeUnit.SECONDS);
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

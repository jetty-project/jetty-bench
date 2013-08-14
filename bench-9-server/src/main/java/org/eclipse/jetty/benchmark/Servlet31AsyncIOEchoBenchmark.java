package org.eclipse.jetty.benchmark;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class Servlet31AsyncIOEchoBenchmark
{
    public static final int BUFFER_SIZE = 16 * 1024;
    private Server server;
    private ServerConnector connector;
    private HttpClient client;
    private String asyncPath = "/async";
    private String syncPath = "/sync";

    public static void main(String[] args) throws Exception
    {
        Servlet31AsyncIOEchoBenchmark benchmark = new Servlet31AsyncIOEchoBenchmark();
        benchmark.prepare();
        benchmark.benchmark();
        benchmark.dispose();
    }

    @Before
    public void prepare() throws Exception
    {
        server = new Server();
        HttpConfiguration configuration = new HttpConfiguration();
//        configuration.setOutputBufferSize(128 * 1024);
        HttpConnectionFactory factory = new HttpConnectionFactory(configuration);
//        factory.setInputBufferSize(128 * 1024);
        connector = new ServerConnector(server, factory);
        connector.setPort(8080);
        server.addConnector(connector);
        ServletContextHandler context = new ServletContextHandler(server, "/", false, false);
        context.addServlet(AsyncServlet.class, asyncPath);
        context.addServlet(SyncServlet.class, syncPath);
        server.start();

        client = new HttpClient();
        client.start();
    }

    @After
    public void dispose() throws Exception
    {
        client.stop();
        server.stop();
    }

    @Test
    public void benchmark() throws Exception
    {
        int runs = 2;
        int iterations = 5_000;
        int[] lengths = new int[]{1024, 4*1024, 16*1024, 64*1024, 256*1024, 1024*1024, 4*1024*1024};
        for (int i = 0; i < runs; ++i)
        {
            for (int l = 0; l < lengths.length; ++l)
            {
                run(iterations, asyncPath, lengths[l]);
                run(iterations, syncPath, lengths[l]);
            }
        }
    }

    private void run(int iterations, String path, int length) throws Exception
    {
        byte[] payload = new byte[length];
        long start = System.nanoTime();
        for (int i = 0; i < iterations; ++i)
            test(path, payload);
        long elapsed = System.nanoTime() - start;
        System.err.printf("%s of %d bytes took %d ms%n", path, length, TimeUnit.NANOSECONDS.toMillis(elapsed));
    }

    private void test(String path, byte[] payload) throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        client.newRequest("localhost", connector.getLocalPort())
                .path(path)
                .content(new BytesContentProvider(payload))
                .send(new Response.CompleteListener()
                {
                    @Override
                    public void onComplete(Result result)
                    {
                        if (result.isSucceeded())
                            latch.countDown();
                    }
                });
        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    public static class AsyncServlet extends HttpServlet
    {
        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            AsyncContext asyncContext = request.startAsync(request, response);
            asyncContext.setTimeout(0);
            Echoer echoer = new Echoer(asyncContext);
            request.getInputStream().setReadListener(echoer);
            response.getOutputStream().setWriteListener(echoer);
        }

        private class Echoer implements ReadListener, WriteListener
        {
            private final byte[] buffer = new byte[BUFFER_SIZE];
            private final AsyncContext asyncContext;
            private final ServletInputStream input;
            private final ServletOutputStream output;
            private boolean complete;

            private Echoer(AsyncContext asyncContext) throws IOException
            {
                this.asyncContext = asyncContext;
                this.input = asyncContext.getRequest().getInputStream();
                this.output = asyncContext.getResponse().getOutputStream();
            }

            @Override
            public void onDataAvailable() throws IOException
            {
                while (input.isReady())
                {
                    // We know we can read now
                    int read = input.read(buffer);

                    // We can write because it's either the first write,
                    // or we just checked that the output was ready,
                    // or we have been called from onWritePossible().
                    output.write(buffer, 0, read);

                    // If we did not write it all, we return
                    // and continue from onWritePossible().
                    if (!output.isReady())
                        return;
                }
                // We wrote everything, so if we
                // read it all then we are done.
                if (input.isFinished())
                {
                    complete = true;
                    asyncContext.complete();
                }
            }

            @Override
            public void onAllDataRead() throws IOException
            {
            }

            @Override
            public void onWritePossible() throws IOException
            {
                if (input.isFinished())
                {
                    if (!complete)
                        asyncContext.complete();
                }
                else
                {
                    onDataAvailable();
                }
            }

            @Override
            public void onError(Throwable failure)
            {
                failure.printStackTrace();
            }
        }
    }

    public static class SyncServlet extends HttpServlet
    {
        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true)
            {
                int read = request.getInputStream().read(buffer, 0, BUFFER_SIZE);
                if (read < 0)
                    break;
                response.getOutputStream().write(buffer, 0, read);
            }
        }
    }
}

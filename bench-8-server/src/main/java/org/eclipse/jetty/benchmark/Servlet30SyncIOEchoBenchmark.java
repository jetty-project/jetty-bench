package org.eclipse.jetty.benchmark;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.Address;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class Servlet30SyncIOEchoBenchmark
{
    public static final int BUFFER_SIZE = 16 * 1024;
    private Server server;
    private Connector connector;
    private HttpClient client;
    private String syncPath = "/sync";

    public static void main(String[] args) throws Exception
    {
        Servlet30SyncIOEchoBenchmark benchmark = new Servlet30SyncIOEchoBenchmark();
        benchmark.prepare();
        benchmark.benchmark();
        benchmark.dispose();
    }

    @Before
    public void prepare() throws Exception
    {
        server = new Server();
        connector = new SelectChannelConnector();
        connector.setPort(8080);
        server.addConnector(connector);
        ServletContextHandler context = new ServletContextHandler(server, "/", false, false);
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
        HttpExchange exchange = new HttpExchange();
        exchange.setAddress(new Address("localhost", connector.getLocalPort()));
        exchange.setRequestURI(path);
        exchange.setRequestContent(new ByteArrayBuffer(payload));
        client.send(exchange);
        exchange.waitForDone();
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

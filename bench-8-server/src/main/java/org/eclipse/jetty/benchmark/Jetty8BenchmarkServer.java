package org.eclipse.jetty.benchmark;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.toolchain.test.BenchmarkHelper;

public class Jetty8BenchmarkServer
{
        
    public static void main(String[] args) throws Exception
    {
        Server server = new Server(8080);

        HandlerCollection handlers = new HandlerCollection();
        ContextHandlerCollection contexts = new ContextHandlerCollection();

        handlers.setHandlers(new Handler[] { contexts, new DefaultHandler() });
        server.setHandler(handlers);
        
        ContextHandler benchmark = new ContextHandler();
        benchmark.setContextPath("/benchmark");
        benchmark.setHandler(new BenchmarkHandler());
        contexts.addHandler(benchmark);
        
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/context");
        context.addServlet(HelloServlet.class,"/hello/*");        
        contexts.addHandler(context);
        
        server.start();
        server.join();
    }

    public static class BenchmarkHandler extends AbstractHandler
    {
        final AtomicBoolean started = new AtomicBoolean();
        final BenchmarkHelper helper = new BenchmarkHelper();

        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
        {
            response.setContentType("text/html;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            baseRequest.setHandled(true);
            PrintWriter out=response.getWriter();
            if (request.getRequestURI().contains("start") && started.compareAndSet(false,true))
                helper.startStatistics();
            
            if (request.getRequestURI().contains("stop") && started.compareAndSet(true,false))
            {
                System.err.println(request.getPathInfo());
                helper.stopStatistics();
            }
            out.println("<html><body>OK</body></html>");
        }
    }

    public static class HelloServlet extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
           
            PrintWriter out=response.getWriter();

            out.println("<html><body><h1>HelloServlet</h1>");
            for (int i=0;i<10;i++)
                out.println("<p>This is some test text. How now brown cow. The rain in spain jumped over the lazy dog</p>");
            out.println("</body></html>");
        }
    }
}

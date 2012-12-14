package org.eclipse.jetty.benchmark;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.toolchain.test.BenchmarkHelper;

public class Jetty9HandlerServer
{
    public static void main(String[] args) throws Exception
    {
        Server server = new Server(8080);
        server.setHandler(new HelloHandler());
        server.start();
        server.join();
    }

    public static class HelloHandler extends AbstractHandler
    {
        final AtomicBoolean started = new AtomicBoolean();
        final BenchmarkHelper helper = new BenchmarkHelper();
        
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
        {
            
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
            baseRequest.setHandled(true);
            

            if (target.startsWith("/benchmark"))
            {
                PrintWriter out=response.getWriter();
                if (target.contains("start") && started.compareAndSet(false,true))
                    helper.startStatistics();
                
                if (target.contains("stop") && started.compareAndSet(true,false))
                {
                    System.err.println(request.getPathInfo());
                    helper.stopStatistics();
                }
                out.println("<html><body>OK</body></html>");
            }
            /*
            PrintWriter out=response.getWriter();

            out.println("<html><body><h1>HelloServlet</h1>");
            for (int i=0;i<10;i++)
                out.println("<p>This is some test text. How now brown cow. The rain in spain jumped over the lazy dog</p>");
            out.println("</body></html>");
            */
        }
    }
}

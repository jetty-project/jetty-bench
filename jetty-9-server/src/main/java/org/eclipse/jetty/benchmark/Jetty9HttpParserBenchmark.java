package org.eclipse.jetty.benchmark;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpParser;
import org.eclipse.jetty.http.HttpVersion;
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
import org.eclipse.jetty.util.BufferUtil;

public class Jetty9HttpParserBenchmark
{
    private final String request = 
        "GET /context/hello/info HTTP/1.1\r\n"+
        "Host: localhost:8080\r\n"+
        "User-Agent: benchmark\r\n"+
        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n"+
        "Accept-Language: en-US,en;q=0.5\r\n"+
        "Accept-Encoding: gzip, deflate\r\n" +
        "Referer: http://testhost/test\r\n"+
        "\r\n";

    final BenchmarkHelper helper = new BenchmarkHelper();
    final HttpFields fields = new HttpFields();
    int requests;
    int headers;
    
    public static void main(String[] args) throws Exception
    {
        Jetty9HttpParserBenchmark bm = new Jetty9HttpParserBenchmark();
        
        bm.test(10);
        bm.test(100);
        bm.test(1000);
        bm.test(10000);
        bm.test(100000);
        bm.test(1000000);
        bm.test(10000000);
    }

    private void test(int iterations)
    {
        helper.startStatistics();
        try
        {
            ByteBuffer buf=BufferUtil.toBuffer(request);
            requests=0;
            headers=0;
            System.err.println("tests    "+iterations);
            
            HttpParser.RequestHandler<ByteBuffer> handler = new MyHandler();
            HttpParser parser = new HttpParser(handler,2048);
            
            for (int i=0;i<iterations;i++)
            {
                parser.parseNext(buf.duplicate());
                parser.reset();
                headers+=fields.size();
                fields.clear();
            }
            System.err.println("requests "+requests);
            System.err.println("headers  "+headers);
        }
        finally
        {
            helper.stopStatistics();
        }
    }


    private class MyHandler implements HttpParser.RequestHandler<ByteBuffer>
    {
        public boolean parsedHeader(HttpField field)
        {
            fields.add(field);
            return false;
        }
        
        public boolean messageComplete()
        {
            requests++;
            return true;
        }
        
        public boolean headerComplete()
        {
            return false;
        }
        
        public boolean earlyEOF()
        {
            return true;
        }
        
        public boolean content(ByteBuffer item)
        {
            return false;
        }
        
        public void badMessage(int status, String reason)
        {                    
        }
        
        public boolean startRequest(HttpMethod method, String methodString, String uri, HttpVersion version)
        {
            return false;
        }
        
        public boolean parsedHostHeader(String host, int port)
        {
            return false;
        }
    };
}

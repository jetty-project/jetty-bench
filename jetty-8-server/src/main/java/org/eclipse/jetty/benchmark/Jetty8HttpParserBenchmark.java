package org.eclipse.jetty.benchmark;

import java.io.IOException;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpParser;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.toolchain.test.BenchmarkHelper;

public class Jetty8HttpParserBenchmark
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
        Jetty8HttpParserBenchmark bm = new Jetty8HttpParserBenchmark();
        
        bm.test(10);
        bm.test(100);
        bm.test(1000);
        bm.test(10000);
        bm.test(100000);
        bm.test(1000000);
        bm.test(10000000);
    }

    private void test(int iterations) throws IOException
    {
        helper.startStatistics();
        try
        {
            ByteArrayBuffer buffer = new ByteArrayBuffer(request,false);
            requests=0;
            headers=0;
            System.err.println("tests    "+iterations);
            
            HttpParser.EventHandler  handler = new MyHandler();
            
            HttpParser parser = new HttpParser(buffer,handler);
            
            for (int i=0;i<iterations;i++)
            {
                parser.parseAvailable();
                parser.setState(HttpParser.STATE_START);
                buffer.setGetIndex(0);
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


    private class MyHandler extends HttpParser.EventHandler
    {

        @Override
        public void content(Buffer arg0) throws IOException
        {
            
        }

        @Override
        public void startRequest(Buffer arg0, Buffer arg1, Buffer arg2) throws IOException
        {
        }

        @Override
        public void startResponse(Buffer arg0, int arg1, Buffer arg2) throws IOException
        {            
        }

        @Override
        public void headerComplete() throws IOException
        {
            requests++;
            super.headerComplete();
        }

        @Override
        public void parsedHeader(Buffer name, Buffer value) throws IOException
        {
            fields.add(name,value);
            super.parsedHeader(name,value);
        }
        
        
    };
}

package org.eclipse.jetty.benchmark;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.toolchain.test.BenchmarkHelper;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.statistic.SampleStatistic;

public class LatencyClient
{   
    private final String request = 
        "GET /context/hello/info HTTP/1.1\r\n"+
        "Host: localhost:8080\r\n"+
        "User-Agent: benchmark\r\n"+
        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n"+
        "Referer: http://testhost/test\r\n"+
        "\r\n";

    private final ByteBuffer requestBuf;
    private final ByteBuffer responseBuf;
    private SocketChannel client;
        
    public LatencyClient()
    {
        requestBuf = BufferUtil.allocateDirect(4096);
        BufferUtil.flipToFill(requestBuf);
        BufferUtil.put(BufferUtil.toBuffer(request),requestBuf);
        BufferUtil.flipToFlush(requestBuf,0);
        
        responseBuf = BufferUtil.allocateDirect(4096);
    }
    
    public void start() throws IOException
    {
        client=SocketChannel.open(new InetSocketAddress("localhost",8080));
    }
    
    public void stop() throws IOException
    {
        if (client.isOpen())
            client.close();
        client=null;
    }
    
    public int requestResponse(int count) throws Exception
    {
        int i=0;
        for (;client.isOpen() && i<count;i++)
            requestResponse();
        return i;
    }

    
    public void requestResponse() throws Exception
    {
        client.write(requestBuf.duplicate());
        BufferUtil.clear(responseBuf);
        do
        {
            int pos=BufferUtil.flipToFill(responseBuf);
            if (client.read(responseBuf)==-1)
                client.close();
            BufferUtil.flipToFlush(responseBuf,pos);            
        }
        while (!BufferUtil.toString(responseBuf).contains("</html>"));
    }
    
        
    public static void main(String... args) throws Exception
    {
        LatencyClient bm = new LatencyClient();
        bm.start();
        System.err.printf("Warmup...%n");
        bm.requestResponse(100000);
        TimeUnit.MILLISECONDS.sleep(500);

        System.err.printf("Measuring...%n");
        SampleStatistic latency=new SampleStatistic();
        
        for (int i=0;i<100;i++)
        {
            TimeUnit.MILLISECONDS.sleep(10);
            long start = System.nanoTime();
            bm.requestResponse();
            long end = System.nanoTime();
            latency.set(end-start);
        }
        
        
        System.err.printf("Latency count=%,d mean=%,.0fns deviation=%,.0f max=%,dns%n",latency.getCount(),latency.getMean(),latency.getStdDev(),latency.getMax());
        
        bm.stop();


    }

}

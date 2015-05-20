package org.eclipse.jetty.benchmark;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.eclipse.jetty.toolchain.test.BenchmarkHelper;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.URIUtil;

public class SerialClient
{
    private final String start = 
        "GET /benchmark/start HTTP/1.1\r\n"+
        "Host: localhost:8080\r\n"+
        "Connection: close\r\n"+
        "\r\n";
    
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
        
    public SerialClient()
    {
        requestBuf = BufferUtil.allocateDirect(4096);
        BufferUtil.flipToFill(requestBuf);
        BufferUtil.put(BufferUtil.toBuffer(request),requestBuf);
        BufferUtil.flipToFlush(requestBuf,0);
        
        responseBuf = BufferUtil.allocateDirect(4096);
    }
    
    public void start() throws IOException
    {
        SocketChannel control = SocketChannel.open(new InetSocketAddress("localhost",8080));
        control.write(BufferUtil.toBuffer(start));
        while (control.isOpen())
        {
            BufferUtil.clear(responseBuf);
            int pos=BufferUtil.flipToFill(responseBuf);
            if (control.read(responseBuf)==-1)
                control.close();
            BufferUtil.flipToFlush(responseBuf,pos); 
        }
        
        client=SocketChannel.open(new InetSocketAddress("localhost",8080));
    }
    
    public void stop(String test,int count,int of) throws IOException
    {
        String stop = 
            "GET "+URIUtil.encodePath("/benchmark/stop/ "+test+" "+count+" of "+of)+" HTTP/1.1\r\n"+
            "Host: localhost:8080\r\n"+
            "Connection: close\r\n"+
            "\r\n";
        
        SocketChannel control = SocketChannel.open(new InetSocketAddress("localhost",8080));
        control.write(BufferUtil.toBuffer(stop));
        while (control.isOpen())
        {
            BufferUtil.clear(responseBuf);
            int pos=BufferUtil.flipToFill(responseBuf);
            if (control.read(responseBuf)==-1)
                control.close();
            BufferUtil.flipToFlush(responseBuf,pos); 
        }
        
        if (client.isOpen())
            client.close();
        client=null;
    }
    
    public int requestResponse(int count) throws Exception
    {
        int i=0;
        for (;client.isOpen() && i<count;i++)
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
        return i;
    }
        
    public static void main(String... args) throws Exception
    {
        int COUNT=100;
        SerialClient bm = new SerialClient();
        bm.start();
        int done=bm.requestResponse(COUNT);
        bm.stop("Serial Requests",done,COUNT);

        COUNT=1000000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Serial Requests",done,COUNT);

        COUNT=1000000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Serial Requests",done,COUNT);
        
        COUNT=1000000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Serial Requests",done,COUNT);
    }

}

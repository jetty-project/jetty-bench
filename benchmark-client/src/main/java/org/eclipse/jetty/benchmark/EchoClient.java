package org.eclipse.jetty.benchmark;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.eclipse.jetty.toolchain.test.BenchmarkHelper;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.URIUtil;

public class EchoClient
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
        
    public EchoClient()
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
    
    public void stop(String test,int count,int of) throws IOException
    {   
        if (client.isOpen())
            client.close();
        client=null;
        System.err.println(test+" "+count+" of "+of);
    }
    
    public int requestResponse(int count) throws Exception
    {
        int i=0;
        for (;client.isOpen() && i<count;i++)
        {
            int len = client.write(requestBuf.duplicate());
            
            BufferUtil.clear(responseBuf);
            
            while (client.isOpen())
            {
                int pos=BufferUtil.flipToFill(responseBuf);
                len=client.read(responseBuf);
                BufferUtil.flipToFlush(responseBuf,pos); 
                
                if (len<0)
                    client.close();
                
                if (responseBuf.remaining()<requestBuf.remaining())
                    continue;
                BufferUtil.clear(responseBuf);
                break;
            }
        }
        return i;
    }
        
    public static void main(String... args) throws Exception
    {
        int COUNT=10;
        EchoClient bm = new EchoClient();
        bm.start();
        int done=bm.requestResponse(COUNT);
        bm.stop("Serial Requests",done,COUNT);

        COUNT=100;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Serial Requests",done,COUNT);

        COUNT=1000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Serial Requests",done,COUNT);

        COUNT=2000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Serial Requests",done,COUNT);

        COUNT=5000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Serial Requests",done,COUNT);

        COUNT=10000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Serial Requests",done,COUNT);

        COUNT=20000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Serial Requests",done,COUNT);

        COUNT=100000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Serial Requests",done,COUNT);

        COUNT=1000000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Serial Requests",done,COUNT);
    }

}

package org.eclipse.jetty.benchmark;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.toolchain.test.BenchmarkHelper;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.URIUtil;

public class ParallelPipelineClient
{
    private final String start = 
        "GET /benchmark/start HTTP/1.1\r\n"+
        "Host: benchmarkControl:8080\r\n"+
        "Connection: close\r\n"+
        "\r\n";
    
    private final String request = 
        "GET /context/hello/info HTTP/1.1\r\n"+
        "Host: localhost:8080\r\n"+
        "User-Agent: benchmark\r\n"+
        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n"+
        "Accept-Language: en-US,en;q=0.5\r\n"+
        "Accept-Encoding: gzip, deflate\r\n" +
        "Referer: http://testhost/test\r\n"+
        "\r\n";

    private final ByteBuffer requestBuf;
    private final ByteBuffer responseBuf;
    private SocketChannel[] client;
        
    public ParallelPipelineClient()
    {
        requestBuf = BufferUtil.allocateDirect(4096);
        BufferUtil.flipToFill(requestBuf);
        BufferUtil.put(BufferUtil.toBuffer(request),requestBuf);
        BufferUtil.flipToFlush(requestBuf,0);
        
        responseBuf = BufferUtil.allocateDirect(64*1024);
    }
    
    public void start() throws IOException
    {
        System.err.println("Start");
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
        
        client = new SocketChannel[8];
        for (int i=0;i<client.length;i++)
            client[i]=SocketChannel.open(new InetSocketAddress("localhost",8080));
    }
    
    public void stop(String test,int count,int of) throws IOException
    {
        System.err.println("Stop "+test+" "+count+" of "+of);
        String stop = 
            "GET "+URIUtil.encodePath("/benchmark/stop/ "+test+" "+count+" of "+of)+" HTTP/1.1\r\n"+
            "Host: benchmarkControl:8080\r\n"+
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

        for (int i=0;i<client.length;i++)
            if (client[i]!=null && client[i].isOpen())
                client[i].close();
        client=null;
    }
    
    public int requestResponse(final int count) throws Exception
    {
        System.err.println("requesting "+count);
        new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    for (int i=0;i<count;i++)
                    {
                        int c=i%client.length;
                        if (!client[c].isOpen())
                            break;
                        client[c].write(requestBuf.duplicate());
                    }
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            }
        }.start();
        
        final CountDownLatch responses = new CountDownLatch(count);
        
        for (int i=0;i<client.length;i++)
        {
            final int c=i;
            final ByteBuffer buf = BufferUtil.allocateDirect(64*1024);
            
            new Thread()
            {
                @Override
                public void run()
                {
                    try
                    {
                        while (client[c].isOpen() && responses.getCount()>0)
                        {
                            BufferUtil.clear(buf);
                            String s="";

                            while (!s.contains("</html>"))
                            {
                                int pos=BufferUtil.flipToFill(buf);
                                if (client[c].read(buf)==-1)
                                {
                                    client[c].close();
                                    break;
                                }
                                BufferUtil.flipToFlush(buf,pos);  
                                s=BufferUtil.toString(buf);
                            }
                            int index=0;
                            while (true)
                            {
                                index=s.indexOf("</html>",index);
                                if (index<0)
                                    break;
                                responses.countDown();
                                index+=7;
                            }
                        }
                    }
                    catch(AsynchronousCloseException e)
                    {
                        
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace();
                    }
                    
                }
            }.start();
        }
        
        responses.await();
        return count;
    }
        
    public static void main(String... args) throws Exception
    {
        int COUNT;
        int done;
        ParallelPipelineClient bm = new ParallelPipelineClient();

        COUNT=10000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Pipeline Requests",done,COUNT);

        COUNT=10000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Pipeline Requests",done,COUNT);

        COUNT=1000000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Pipeline Requests",done,COUNT);

        COUNT=1000000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Pipeline Requests",done,COUNT);
        

        COUNT=1000000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Pipeline Requests",done,COUNT);

        COUNT=1000000;
        bm.start();
        done=bm.requestResponse(COUNT);
        bm.stop("Pipeline Requests",done,COUNT);
        /*
    */
    }

}

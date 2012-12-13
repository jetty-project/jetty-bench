package org.eclipse.jetty.benchmark;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.eclipse.jetty.io.ChannelEndPoint;
import org.eclipse.jetty.toolchain.test.BenchmarkHelper;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.thread.Scheduler;
import org.eclipse.jetty.util.thread.TimerScheduler;

public class EchoChannelEndPoint9Server implements Runnable
{
    static BenchmarkHelper __helper = new BenchmarkHelper();
    final ChannelEndPoint _endp;
    int _fills;
    long _filled;
    
    public EchoChannelEndPoint9Server(Scheduler scheduler, SocketChannel connection)
    {
        _endp= new ChannelEndPoint(scheduler,connection);
    }

    @Override
    public void run()
    {
        __helper.startStatistics();
        try
        {
            ByteBuffer buffer = BufferUtil.allocate(4096);
            
            while (_endp.isOpen())
            {
                int len=_endp.fill(buffer);
                if (len<0)
                    _endp.close();
                else
                {
                    _fills++;
                    _filled+=len;
                }
                              
                while (buffer.hasRemaining())
                    _endp.flush(buffer);
                BufferUtil.clear(buffer);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            System.err.println("Filled "+_filled+" bytes in "+_fills+" fills");
            __helper.stopStatistics();
        }
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(8080));
        TimerScheduler scheduler = new TimerScheduler();
        scheduler.start();
        
        while (server.isOpen())
        {
            SocketChannel connection = server.accept();
            
            new Thread(new EchoChannelEndPoint9Server(scheduler,connection)).start();
        }
        scheduler.stop();
    }


}

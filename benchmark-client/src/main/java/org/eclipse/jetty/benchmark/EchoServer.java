package org.eclipse.jetty.benchmark;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.eclipse.jetty.toolchain.test.BenchmarkHelper;
import org.eclipse.jetty.util.BufferUtil;

public class EchoServer implements Runnable
{
    static BenchmarkHelper __helper = new BenchmarkHelper();
    final SocketChannel _connection;
    int _fills;
    long _filled;
    
    public EchoServer(SocketChannel connection)
    {
        _connection=connection;
    }

    @Override
    public void run()
    {
        __helper.startStatistics();
        try
        {
            ByteBuffer buffer = BufferUtil.allocate(4096);
            
            while (_connection.isOpen())
            {
                int pos=BufferUtil.flipToFill(buffer);
                int len=_connection.read(buffer);
                BufferUtil.flipToFlush(buffer,pos);
                if (len<0)
                    _connection.close();
                else
                {
                    _fills++;
                    _filled+=len;
                }
                              
                while (buffer.hasRemaining())
                {
                    len=_connection.write(buffer);
                }
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
        
        while (server.isOpen())
        {
            SocketChannel connection = server.accept();
            
            new Thread(new EchoServer(connection)).start();
            
        }
    }


}

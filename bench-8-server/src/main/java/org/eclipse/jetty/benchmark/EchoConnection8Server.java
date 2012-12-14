package org.eclipse.jetty.benchmark;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.AsyncEndPoint;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.Buffers;
import org.eclipse.jetty.io.Buffers.Type;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.PooledBuffers;
import org.eclipse.jetty.io.nio.AsyncConnection;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.toolchain.test.BenchmarkHelper;

public class EchoConnection8Server
{
    static BenchmarkHelper __helper = new BenchmarkHelper();
    
    
    /* ------------------------------------------------------------ */
    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        Server server = new Server();
        EchoConnector connector=new EchoConnector();
        connector.setPort(8080);
        server.addConnector(connector);
        server.start();
        server.join();
    }
 
    static class EchoConnector extends SelectChannelConnector
    {
        Buffers pool = new PooledBuffers(Type.INDIRECT,4096,Type.INDIRECT,4096,Type.INDIRECT,4096);
        
        @Override
        protected AsyncConnection newConnection(SocketChannel channel, AsyncEndPoint endpoint)
        {
            return new EchoConnection(endpoint,pool);
        }
        
    };
    
    static class EchoConnection extends AbstractConnection implements AsyncConnection
    {
        Buffer _buffer;
        final Buffers _bufferPool;
        int _fills;
        long _filled;
        
        public EchoConnection(EndPoint endp, Buffers pool)
        {
            super(endp);
            _bufferPool=pool;
            __helper.startStatistics();
            
        }

        @Override
        public Connection handle() throws IOException
        {

            try
            {
                if (_buffer==null)
                    _buffer=_bufferPool.getBuffer();
                    
                while(true)
                {
                    EndPoint endp=getEndPoint();
                    int len=endp.fill(_buffer);
                    if (len<0)
                        endp.close();

                    if (len<=0)
                        break;

                    _fills++;
                    _filled+=len;

                    while (_buffer.hasContent())
                    {
                        endp.flush(_buffer);
                        if (_buffer.hasContent())
                            endp.blockWritable(30000);
                    }
                }

            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
            if (_bufferPool!=null && _buffer!=null)
            {
                _bufferPool.returnBuffer(_buffer);
                _buffer=null;
            }
            
            return this;
        }

        @Override
        public boolean isIdle()
        {
            return false;
        }

        @Override
        public boolean isSuspended()
        {
            return false;
        }

        @Override
        public void onClose()
        {
            System.err.println("Filled "+_filled+" bytes in "+_fills+" fills");
            __helper.stopStatistics();
            
        }

        @Override
        public void onInputShutdown() throws IOException
        {
            
        }
        
    }
}

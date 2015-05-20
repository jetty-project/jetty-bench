package org.eclipse.jetty.benchmark;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.AbstractConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.toolchain.test.BenchmarkHelper;
import org.eclipse.jetty.util.BlockingCallback;
import org.eclipse.jetty.util.BufferUtil;

public class EchoConnection9Server
{
    static BenchmarkHelper __helper = new BenchmarkHelper();
    static ByteBufferPool __bufferPool;
    
    
    /* ------------------------------------------------------------ */
    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        Server server = new Server();
        __bufferPool= new ArrayByteBufferPool();
     
        ServerConnector connector = new ServerConnector(server,new EchoConnectionFactory());
        connector.setPort(8080);
        server.addConnector(connector);
        server.start();
        server.join();
    }
    
    public static class EchoConnectionFactory extends AbstractConnectionFactory
    {
        public EchoConnectionFactory()
        {
            super("echo");
        }

        @Override
        public Connection newConnection(Connector connector, EndPoint endPoint)
        {
            return new EchoConnection(__bufferPool,endPoint,connector.getExecutor());
        }
    }

    
    public static class EchoConnection extends AbstractConnection
    {
        final ByteBufferPool _bufferPool;
        ByteBuffer _buffer = BufferUtil.allocate(4096);
        BlockingCallback _callback = new BlockingCallback();
        int _fills;
        long _filled;
        
        public EchoConnection(ByteBufferPool pool, EndPoint endp, Executor executor)
        {
            super(endp,executor);
            _bufferPool=pool;
            if (_bufferPool==null)
                _buffer = BufferUtil.allocate(4096);
        }

        @Override
        public void onOpen()
        {
            super.onOpen();
            __helper.startStatistics();
            fillInterested();
        }
        
        @Override
        public void onClose()
        {
            System.err.println("Filled "+_filled+" bytes in "+_fills+" fills");
            __helper.stopStatistics();
            super.onClose();
        }

        @Override
        public void onFillable()
        {
            try
            {
                if (_buffer==null)
                    _buffer=_bufferPool.acquire(4096,false);
                    
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

                    if (_buffer.hasRemaining())
                    {
                        endp.write(_callback,_buffer);
                        _callback.block();
                    }
                }

            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
            if (_bufferPool!=null && _buffer!=null)
            {
                _bufferPool.release(_buffer);
                _buffer=null;
            }
            fillInterested();
        }
        
    }
}

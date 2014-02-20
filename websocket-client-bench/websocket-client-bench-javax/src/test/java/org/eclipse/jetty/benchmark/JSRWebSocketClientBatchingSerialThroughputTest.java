//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.benchmark;

import java.io.IOException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import org.eclipse.jetty.io.RuntimeIOException;

public class JSRWebSocketClientBatchingSerialThroughputTest extends JSRWebSocketClientSerialThroughputTest
{
    @Override
    protected Class<? extends Endpoint> websocketServerClass()
    {
        return BatchingServerWebSocket.class;
    }

    @Override
    protected void configureClientSession(Session session) throws IOException
    {
        session.getBasicRemote().setBatchingAllowed(true);
    }

    @Override
    protected void perform(Session session, char[] chars, int currentRun, int iterations) throws IOException
    {
        super.perform(session, chars, currentRun, iterations);
        // Tell the server to flush too.
        test(session, BatchingServerWebSocket.FLUSH_MESSAGE);
        session.getBasicRemote().flushBatch();
    }

    public static class BatchingServerWebSocket extends ServerWebSocket
    {
        private static final String FLUSH_MESSAGE = "FLUSH";

        @Override
        public void onOpen(Session session, EndpointConfig config)
        {
            try
            {
                super.onOpen(session, config);
                session.getBasicRemote().setBatchingAllowed(true);
            }
            catch (IOException x)
            {
                throw new RuntimeIOException(x);
            }
        }

        @Override
        public void onMessage(String message)
        {
            try
            {
                if (FLUSH_MESSAGE.equals(message))
                    session.getBasicRemote().flushBatch();
                else
                    super.onMessage(message);
            }
            catch (IOException x)
            {
                close(session, x);
            }
        }
    }
}

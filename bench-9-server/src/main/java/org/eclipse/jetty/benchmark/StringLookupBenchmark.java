package org.eclipse.jetty.benchmark;

import java.nio.ByteBuffer;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpParser;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.toolchain.test.BenchmarkHelper;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Trie;

public class StringLookupBenchmark
{
    String[][] lookup =
    {
        { "Host: blah","Host" },
        { "User-Agent: blah","User-Agent" },
        { "Accept: something","Accept" },
        { "Accept-Language: asdfasd","Accept-Language" },
        { "Accept-Encoding: asdfasdf","Accept-Encoding" },
        { "Referer: asdfasdf","Referer" }
    };
    
    final BenchmarkHelper helper = new BenchmarkHelper();
    
    public static void main(String[] args) throws Exception
    {
        StringLookupBenchmark bm = new StringLookupBenchmark();
        
        bm.test(10);
        bm.test(100);
        bm.test(1000);
        bm.test(10000);
        bm.test(100000);
        bm.test(1000000);
        bm.test(10000000);
        bm.test(100000000);
    }

    private void test(int iterations)
    {
        ByteBuffer buf[] = new ByteBuffer[lookup.length];
        Trie<String> trie=new Trie<>();
        int i=0;
        for (String[] test : lookup)
        {
            buf[i++]=BufferUtil.toBuffer(test[0]);
            trie.put(test[1]);
        }
        
        
        helper.startStatistics();
        
        System.err.println(iterations+" "+trie);
        try
        {
            for (i=0;i<iterations;i++)
            {
                for (int t=0;t<lookup.length;t++)
                {
                    String best=trie.getBest(buf[t],0,buf[t].remaining());
                    if (best!=lookup[t][1])
                        System.err.println("best="+best+" for "+BufferUtil.toString(buf[t]));
                }
            }
        }
        finally
        {
            helper.stopStatistics();
        }
    }

}

package org.eclipse.jetty.benchmark;

import java.nio.ByteBuffer;

import org.eclipse.jetty.toolchain.test.BenchmarkHelper;
import org.eclipse.jetty.util.ArrayTernaryTrie;
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
        System.err.printf("%d -> %d%n",Integer.MAX_VALUE,ArrayTernaryTrie.hilo(Integer.MAX_VALUE));
        System.err.printf("%d -> %d%n",55,ArrayTernaryTrie.hilo(55));
        System.err.printf("%d -> %d%n",54,ArrayTernaryTrie.hilo(54));
        System.err.printf("%d -> %d%n",1,ArrayTernaryTrie.hilo(1));
        System.err.printf("%d -> %d%n",0,ArrayTernaryTrie.hilo(0));
        System.err.printf("%d -> %d%n",-1,ArrayTernaryTrie.hilo(-1));
        System.err.printf("%d -> %d%n",-35,ArrayTernaryTrie.hilo(-35));
        System.err.printf("%d -> %d%n",-36,ArrayTernaryTrie.hilo(-36));
        System.err.printf("%d -> %d%n",Integer.MIN_VALUE,ArrayTernaryTrie.hilo(Integer.MIN_VALUE));
        
        // System.exit(1);
        
        StringLookupBenchmark bm = new StringLookupBenchmark();
        
        bm.test(10000000);
        bm.test(10000000);
        bm.test(10000000);
    }

    private void test(int iterations)
    {
        ByteBuffer buf[] = new ByteBuffer[lookup.length];
        Trie<String> trie=new ArrayTernaryTrie<>();
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

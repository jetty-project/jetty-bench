package org.eclipse.jetty.benchmark;

import java.util.Random;

import org.eclipse.jetty.toolchain.test.BenchmarkHelper;

public class HexToDecimalBenchmark
{
    static Random random = new Random();
    
    final BenchmarkHelper helper = new BenchmarkHelper();
    
    public static void main(String[] args) throws Exception
    {
        HexToDecimalBenchmark bm = new HexToDecimalBenchmark();

        int size=100000000;
        char[] chars = new char[size];
        for (int i=0;i<size;i++)
            chars[i]="0123456789ABCDEF".charAt(random.nextInt(16));

            bm.testBranching(chars);
            bm.testBranchless(chars);
            bm.testBranching(chars);
            bm.testBranchless(chars);
            bm.testBranching(chars);
            bm.testBranchless(chars);
    }

    private void testBranching(char[] chars)
    {
        // System.err.println(new String(chars));
        
        try
        {
            helper.startStatistics();
            System.err.println(chars.length+" branching ="+hex2decBranching(chars));
        }
        finally
        {
            helper.stopStatistics();
        }
    }

    private void testBranchless(char[] chars)
    {
        // System.err.println(new String(chars));
        try
        {
            helper.startStatistics();
            System.err.println(chars.length+" branchless="+hex2decBranchless(chars));
        }
        finally
        {
            helper.stopStatistics();
        }
    }
    
    private int hex2decBranchless(char[] chars)
    {
        int result=0;
        for (char c:chars)
        {
            byte b = (byte)((c & 0x1f) + ((c >> 6) * 0x19) - 0x10);
            // System.err.printf("%s -> %d%n",c,b);
            result=(b+result)&0x7fffffff;
        }
        return result;
    }
    
    private int hex2decBranching(char[] chars)
    {
        int result=0;
        for (char c:chars)
        {
            byte b=(c>='A'&&c<='F')
              ?(byte)(10+c-'A')
              :(byte)(c-'0');
            // System.err.printf("%s -> %d%n",c,b);
            result=(b+result)&0x7fffffff;
        }
        return result;
    }

}

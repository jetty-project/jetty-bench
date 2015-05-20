package org.eclipse.jetty.benchmark;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class UnmaskingBenchmark
{
    public static void main(String[] args)
    {
        byte[] maskBytes = {0x12, 0x6F, 0x3D, 0x41};
        int maskInt = ByteBuffer.wrap(maskBytes).getInt();

        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

        int runs = 5;
        int iterations = 1_000_000;
        for (int i = 0; i < runs; ++i)
            runUnmaskByte(buffer, maskBytes, iterations);
        for (int i = 0; i < runs; ++i)
            runUnmaskInt(buffer, maskInt, iterations);
    }

    private static void runUnmaskByte(ByteBuffer buffer, byte[] mask, int iterations)
    {
        long start = System.nanoTime();
        for (int i = 0; i < iterations; ++i)
            testUnmaskByte(buffer, mask);
        long elapsed = System.nanoTime() - start;
        System.err.printf("Unmask byte took %d ms%n", TimeUnit.NANOSECONDS.toMillis(elapsed));
    }

    private static void testUnmaskByte(ByteBuffer buffer, byte[] mask)
    {
        for (int i = buffer.position(); i < buffer.limit(); ++i)
        {
            buffer.put(i, (byte)(buffer.get(i) ^ mask[i & 0x3]));
        }
    }

    private static void runUnmaskInt(ByteBuffer buffer, int mask, int iterations)
    {
        long start = System.nanoTime();
        for (int i = 0; i < iterations; ++i)
            testUnmaskInt(buffer, mask);
        long elapsed = System.nanoTime() - start;
        System.err.printf("Unmask int took %d ms%n", TimeUnit.NANOSECONDS.toMillis(elapsed));
    }

    private static void testUnmaskInt(ByteBuffer buffer, int mask)
    {
        for (int i = buffer.position(); i < buffer.limit(); i += 4)
        {
            if (buffer.remaining() >= 4)
                buffer.putInt(i, buffer.getInt(i) ^ mask);
        }
    }
}

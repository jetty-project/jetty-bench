package org.eclipse.jetty.benchmark;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks UTF-8 encoding of a String into a {@link ByteBuffer}.
 * This is a key hotspot in WebSocket, for example, when sending String frames.
 */
public class CharsetEncoderBenchmark
{
    public static void main(String[] args)
    {
        char[] chars = new char[1024];
        Arrays.fill(chars, 'x');
        String data = new String(chars);

        Charset charset = Charset.forName("UTF-8");
        ByteBuffer buffer = ByteBuffer.allocate(2048);

        int runs = 5;
        int iterations = 100_000;
        for (int i = 0; i < runs; ++i)
            runGetBytes(charset, data, buffer, iterations);
        for (int i = 0; i < runs; ++i)
            runGetBytes(charset, data, buffer, iterations);

        for (int i = 0; i < runs; ++i)
            runEncode(charset, data, buffer, iterations);
        for (int i = 0; i < runs; ++i)
            runEncode(charset, data, buffer, iterations);
    }

    private static void runGetBytes(Charset charset, String data, ByteBuffer buffer, int iterations)
    {
        long start = System.nanoTime();
        for (int i = 0; i < iterations; ++i)
        {
            buffer.clear();
            // Allocates a byte[] in getBytes(), which is then wrapped.
            // Fast, but allocates a lot.
            buffer = ByteBuffer.wrap(data.getBytes(charset));
        }
        long elapsed = System.nanoTime() - start;
        System.err.printf("GetBytes took %d ms%n", TimeUnit.NANOSECONDS.toMillis(elapsed));
    }

    private static void runEncode(Charset charset, String data, ByteBuffer buffer, int iterations)
    {
        long start = System.nanoTime();
        for (int i = 0; i < iterations; ++i)
        {
            buffer.clear();
            // Does no allocation, as the encoding is done in place.
            // However, CharBuffer.wrap(String) produces a CharBuffer
            // where hasArray() returns false. This means that instead
            // of copying the byte[] it does - for ASCII -
            // byteBuffer.put(charBuffer.get()), which is 4-5x slower.
            // Cannot extend CharBuffer since its constructors are
            // package private.
            // If we could pass a char[] instead of a String, then
            // the CharBuffer would have hasArray() return true, but
            // we cannot extract the char[] from String without using
            // reflection.
            charset.newEncoder().encode(CharBuffer.wrap(data), buffer, true);
        }
        long elapsed = System.nanoTime() - start;
        System.err.printf("Encoding took %d ms%n", TimeUnit.NANOSECONDS.toMillis(elapsed));
    }
}

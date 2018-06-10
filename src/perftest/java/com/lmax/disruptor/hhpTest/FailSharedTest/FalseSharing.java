package com.lmax.disruptor.hhpTest.FailSharedTest;

public final class FalseSharing implements Runnable
{
    public final static int NUM_THREADS = 4; // change
    public final static long ITERATIONS = 500L * 1000L * 1000L;
    private final int arrayIndex;

    private static VolatileLong[] longs = new VolatileLong[NUM_THREADS];
    static
    {
        for (int i = 0; i < longs.length; i++)
        {
            longs[i] = new VolatileLong();
        }
    }

    public FalseSharing(final int arrayIndex)
    {
        this.arrayIndex = arrayIndex;
    }

    public static void main(final String[] args) throws Exception
    {
        final long start = System.nanoTime();
        runTest();
        System.out.println("duration = " + (System.nanoTime() - start));
    }

    private static void runTest() throws InterruptedException
    {
        Thread[] threads = new Thread[NUM_THREADS];

        for (int i = 0; i < threads.length; i++)
        {
            threads[i] = new Thread(new FalseSharing(i));
        }

        for (Thread t : threads)
        {
            t.start();
        }

        for (Thread t : threads)
        {
            t.join();
        }
    }

    public void run()
    {
        long i = ITERATIONS + 1;
        while (0 != --i)
        {
            longs[arrayIndex].value = i;
        }
    }

    public final static class VolatileLong
    {
//        protected long p1, p2, p3, p4, p5, p6, p7;  // comment on
        public volatile long value = 0L;
//        protected long p9, p10, p11, p12, p13, p14,p15;  // comment on

        // 防止编译器优化
        public static long preventFromOptimization(VolatileLong v) {
//            return v.p1 + v.p2 + v.p3 + v.p4 + v.p5 + v.p6
//                    +v.p7+v.p9+v.p10+v.p11+v.p12+v.p13+v.p14+v.p15;
            return 0;
        }
    }



}
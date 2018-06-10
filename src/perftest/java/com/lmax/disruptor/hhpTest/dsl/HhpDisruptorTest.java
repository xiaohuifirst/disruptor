package com.lmax.disruptor.hhpTest.dsl;

import com.lmax.disruptor.AbstractPerfTestDisruptor;
import com.lmax.disruptor.PerfTestContext;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class HhpDisruptorTest extends AbstractPerfTestDisruptor
{
    private static final int NUM_PUBLISHERS = 3;
    private static final long ITERATIONS = 1000*1000*30L;
    private final ExecutorService executor =
            Executors.newFixedThreadPool(NUM_PUBLISHERS, DaemonThreadFactory.INSTANCE);
    private final CyclicBarrier cyclicBarrier = new CyclicBarrier(NUM_PUBLISHERS+1);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private static final class ProduceTask implements Runnable{

        private final CyclicBarrier cyclicBarrier;
        private final long iterations;
        private final AsyncMsgProcessor msgProcessor;

        public ProduceTask(final CyclicBarrier cyclicBarrier, final long iterations){
            this.cyclicBarrier = cyclicBarrier;
            this.iterations = iterations;
            msgProcessor = new AsyncMsgProcessor();
        }

        @Override
        public void run()
        {
            try
            {
                cyclicBarrier.await();

                for (long i = 0; i < iterations; i++)
                {
//                    msgProcessor.produceMessage(UUID.randomUUID().toString(), "+"+i,"-"+i);
                    msgProcessor.produceMessage("hhp"+i, "+"+i,"-"+i);
                }
            }
            catch (Exception ex)
            {
                throw new RuntimeException(ex);
            }
        }
    }
    private final ProduceTask[] publishers = new ProduceTask[NUM_PUBLISHERS];

    {
        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            publishers[i] = new ProduceTask(cyclicBarrier, ITERATIONS / NUM_PUBLISHERS);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected int getRequiredProcessorCount()
    {
        return 4;
    }

    @Override
    protected PerfTestContext runDisruptorPass() throws Exception
    {
        PerfTestContext perfTestContext = new PerfTestContext();
        final CountDownLatch latch = new CountDownLatch(1);

        Future<?>[] futures = new Future[NUM_PUBLISHERS];
        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            futures[i] = executor.submit(publishers[i]);
        }

        long start = System.currentTimeMillis();
        cyclicBarrier.await();

        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            futures[i].get();
        }

        // 关闭disruptor
        AsyncMsgProcessor.shutdownDisruptor();

        perfTestContext.setDisruptorOps((ITERATIONS * 1000L) / (System.currentTimeMillis() - start));
        return perfTestContext;
    }

    public static void main(String[] args) throws Exception
    {
        new HhpDisruptorTest().testImplementations();
    }
}


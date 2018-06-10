package com.lmax.disruptor.hhpTest.origin;

import com.lmax.disruptor.AbstractPerfTestDisruptor;
import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.PerfTestContext;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.hhpTest.support.MsgEvent;
import com.lmax.disruptor.hhpTest.support.MsgEventHandler;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.lmax.disruptor.RingBuffer.createMultiProducer;


public final class HhpSequencedTest extends AbstractPerfTestDisruptor
{
    private static final int NUM_PUBLISHERS = 3;
    private static final int BUFFER_SIZE = 1024 * 64;
    private static final long ITERATIONS = 1000L * 1000L * 30L;
    private final ExecutorService executor =
            Executors.newFixedThreadPool(NUM_PUBLISHERS + 1, DaemonThreadFactory.INSTANCE);
    private final CyclicBarrier cyclicBarrier = new CyclicBarrier(NUM_PUBLISHERS + 1);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<MsgEvent> ringBuffer =
            createMultiProducer(MsgEvent.FACTORY, BUFFER_SIZE, new BusySpinWaitStrategy());

    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    private final MsgEventHandler handler = new MsgEventHandler();
    private final BatchEventProcessor<MsgEvent> batchEventProcessor =
            new BatchEventProcessor<>(ringBuffer, sequenceBarrier, handler);
    private final MsgPublisher[] msgPublishers = new MsgPublisher[NUM_PUBLISHERS];

    {
        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            msgPublishers[i] = new MsgPublisher(cyclicBarrier, ringBuffer, ITERATIONS / NUM_PUBLISHERS);
        }

        ringBuffer.addGatingSequences(batchEventProcessor.getSequence());
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

        Future<?>[] futures = new Future[NUM_PUBLISHERS];
        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            futures[i] = executor.submit(msgPublishers[i]);
        }
        executor.submit(batchEventProcessor);

        long start = System.currentTimeMillis();
        cyclicBarrier.await();

        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            futures[i].get();
        }

        perfTestContext.setDisruptorOps((ITERATIONS * 1000L) / (System.currentTimeMillis() - start));
        batchEventProcessor.halt();

        return perfTestContext;
    }

    public static void main(String[] args) throws Exception
    {
        new HhpSequencedTest().testImplementations();
    }
}

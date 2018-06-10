package com.lmax.disruptor.hhpTest.queue;

import com.lmax.disruptor.AbstractPerfTestQueue;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.hhpTest.support.MsgEvent;
import com.lmax.disruptor.queue.ThreeToOneQueueThroughputTest;
import com.lmax.disruptor.support.ValueAdditionQueueProcessor;
import com.lmax.disruptor.support.ValueQueuePublisher;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

//public class HhpQueueTest extends AbstractPerfTestQueue {
//
//    private BlockingQueue<MsgEvent> msgEventQueue = new ArrayBlockingQueue<>(1024 * 64);
//    private static final int NUM_PUBLISHERS = 3;
//    private static final long ITERATIONS = 1000*1000*30L;
//    private final ExecutorService executor =
//            Executors.newFixedThreadPool(NUM_PUBLISHERS, DaemonThreadFactory.INSTANCE);
//    private final CyclicBarrier cyclicBarrier = new CyclicBarrier(NUM_PUBLISHERS+1);
//
//    private final MsgConsumer queueConsumer =
//            new MsgConsumer(msgEventQueue, ((ITERATIONS / NUM_PUBLISHERS) * NUM_PUBLISHERS) - 1L);
//    private final ValueQueuePublisher[] valueQueuePublishers = new ValueQueuePublisher[NUM_PUBLISHERS];
//
//    {
//        for (int i = 0; i < NUM_PUBLISHERS; i++)
//        {
//            valueQueuePublishers[i] =
//                    new ValueQueuePublisher(cyclicBarrier, blockingQueue, ITERATIONS / NUM_PUBLISHERS);
//        }
//    }
//
//    ///////////////////////////////////////////////////////////////////////////////////////////////
//
//    @Override
//    protected int getRequiredProcessorCount()
//    {
//        return 4;
//    }
//
//    @Override
//    protected long runQueuePass() throws Exception
//    {
//        final CountDownLatch latch = new CountDownLatch(1);
//        queueProcessor.reset(latch);
//
//        Future<?>[] futures = new Future[NUM_PUBLISHERS];
//        for (int i = 0; i < NUM_PUBLISHERS; i++)
//        {
//            futures[i] = executor.submit(valueQueuePublishers[i]);
//        }
//        Future<?> processorFuture = executor.submit(queueProcessor);
//
//        long start = System.currentTimeMillis();
//        cyclicBarrier.await();
//
//        for (int i = 0; i < NUM_PUBLISHERS; i++)
//        {
//            futures[i].get();
//        }
//
//        latch.await();
//
//        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
//        queueProcessor.halt();
//        processorFuture.cancel(true);
//
//        return opsPerSecond;
//    }
//
//    public static void main(String[] args) throws Exception
//    {
//        new ThreeToOneQueueThroughputTest().testImplementations();
//    }
//
//
//}

package com.lmax.disruptor.hhpTest.queue;

import com.lmax.disruptor.hhpTest.support.MsgEvent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;

public class MsgPublisher implements Runnable {

    private final BlockingQueue<MsgEvent> queue;
    private final CyclicBarrier cyclicBarrier;
    private final long iterations;
    private final ThreadLocal<MsgEvent> threadLocalMsg = new ThreadLocal<MsgEvent>(){

        @Override
        protected MsgEvent initialValue() {
            return new MsgEvent();
        }
    };

    public MsgPublisher(final CyclicBarrier cyclicBarrier, final BlockingQueue<MsgEvent> queue, final long iterations){
        this.queue = queue;
        this.cyclicBarrier = cyclicBarrier;
        this.iterations = iterations;
    }

    @Override
    public void run() {
        try {
            cyclicBarrier.await();

            for (long i = 0; i < iterations; i++) {
                threadLocalMsg.get().setValues(null, "hh"+i,
                        System.currentTimeMillis(), "+" + i, "-" + i);
                queue.put(threadLocalMsg.get());
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}

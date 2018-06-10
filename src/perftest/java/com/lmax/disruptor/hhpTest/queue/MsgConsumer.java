package com.lmax.disruptor.hhpTest.queue;

import com.lmax.disruptor.hhpTest.support.MsgEvent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;

public class MsgConsumer implements Runnable {

    private final BlockingQueue<MsgEvent> queue;
    private final long iterations;

    public MsgConsumer(final BlockingQueue<MsgEvent> queue, final long iterations){
        this.queue = queue;
        this.iterations = iterations;
    }

    @Override
    public void run() {
        try {

            long counter = 0;
            do{
                MsgEvent event = queue.take();
                event.execute(true);
            }while (++counter < iterations);
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}

package com.lmax.disruptor.hhpTest.origin;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.hhpTest.support.MsgEvent;
import com.lmax.disruptor.support.ValueEvent;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;

public class MsgPublisher implements Runnable{

    private final CyclicBarrier cyclicBarrier;
    private final RingBuffer<MsgEvent> ringBuffer;
    private final long iterations;

    public MsgPublisher(
            final CyclicBarrier cyclicBarrier, final RingBuffer<MsgEvent> ringBuffer, final long iterations)
    {
        this.cyclicBarrier = cyclicBarrier;
        this.ringBuffer = ringBuffer;
        this.iterations = iterations;
    }

    @Override
    public void run()
    {
        try {
            cyclicBarrier.await();

            for (long i = 0; i < iterations; i++) {
                long sequence = ringBuffer.next();
                MsgEvent event = ringBuffer.get(sequence);
                event.setValues(null, "hh"+i,
                        System.currentTimeMillis(), "+" + i, "-" + i);
                ringBuffer.publish(sequence);
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}

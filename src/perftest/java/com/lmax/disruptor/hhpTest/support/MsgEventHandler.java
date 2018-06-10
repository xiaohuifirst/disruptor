package com.lmax.disruptor.hhpTest.support;

import com.lmax.disruptor.BatchStartAware;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceReportingEventHandler;

import java.util.concurrent.CountDownLatch;

/**
 * 消费者用来处理LongEvent的类
 */
public class MsgEventHandler implements
        SequenceReportingEventHandler<MsgEvent>, LifecycleAware,BatchStartAware {

    private static final int NOTIFY_PROGRESS_THRESHOLD = 50;
    private Sequence sequenceCallback;
    private int counter;
    private long threadId = -1;

    private long count;
    private CountDownLatch latch;

    public void reset(final CountDownLatch latch, final long expectedCount)
    {
        this.latch = latch;
        count = expectedCount;
    }

    @Override
    public void onEvent(final MsgEvent event, final long sequence, final boolean endOfBatch) throws Exception {

        event.execute(endOfBatch);
        event.clear();

        // notify the BatchEventProcessor that the sequence has progressed.
        // Without this callback the sequence would not be progressed
        // until the batch has completely finished.
        // 设置之后，后续依赖这个sequence的消费者才能消费（应用场景：log4j2批量写日志）
        // 详见 {@link SequenceReportingEventHandler}接口的描述
        if(++counter > NOTIFY_PROGRESS_THRESHOLD){
            // 更新本sequence的值
            sequenceCallback.set(sequence);
            counter = 0;
        }

        // 直接使用Disruptor类的话，好像不行
//        if (count == sequence)
//        {
//            latch.countDown();
//        }

    }

    @Override
    public void onStart() {
        // 线程启动时，获取线程ID
        threadId = Thread.currentThread().getId();
    }

    @Override
    public void onShutdown() {
        // do nothing
    }

    @Override
    public void setSequenceCallback(final Sequence sequenceCallback) {
        this.sequenceCallback = sequenceCallback;
    }

    @Override
    public void onBatchStart(long batchSize) {
        // do nothing
    }
}

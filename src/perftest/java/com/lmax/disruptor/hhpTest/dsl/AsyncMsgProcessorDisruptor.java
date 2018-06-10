package com.lmax.disruptor.hhpTest.dsl;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.hhpTest.support.MsgEvent;
import com.lmax.disruptor.hhpTest.support.MsgEventDefaultExceptionHandler;
import com.lmax.disruptor.hhpTest.support.MsgEventHandler;
import com.lmax.disruptor.hhpTest.support.MsgEventTranslator;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class AsyncMsgProcessorDisruptor {
    private static final int SLEEP_MILLIS_BETWEEN_DRAIN_ATTEMPTS = 50;
    private static final int MAX_DRAIN_ATTEMPTS_BEFORE_SHUTDOWN = 200;

    private volatile Disruptor<MsgEvent> disruptor;
    private boolean useThreadLocalTranslator = true;
    private int ringBufferSize;

    // =-===========================第一种用法：使用ThreadLocal=====================================
    private static final ThreadLocal<Disruptor<MsgEvent>> THREAD_LOCAL_DISRUPTOR = new ThreadLocal<Disruptor<MsgEvent>>(){

        @Override
        protected Disruptor<MsgEvent> initialValue() {
            Disruptor<MsgEvent> disruptor = new Disruptor<MsgEvent>(MsgEvent.FACTORY,8*1024, Executors.newCachedThreadPool(),
                    ProducerType.MULTI, new BlockingWaitStrategy());
            disruptor.handleEventsWith(new MsgEventHandler());
            disruptor.setDefaultExceptionHandler(new MsgEventDefaultExceptionHandler());
            disruptor.start();
            return disruptor;
        }
    };

    // -----========================第一种方法：直接修改event对象来publish------------------------=======
    public void publish(String sessionId, String hopByhop) throws Exception{

        RingBuffer<MsgEvent> ringBuffer = THREAD_LOCAL_DISRUPTOR.get().getRingBuffer();
        long next = ringBuffer.next();
        try{
            MsgEvent msgEvent = ringBuffer.get(next);
            msgEvent.setValues(null,sessionId,System.currentTimeMillis(),hopByhop,null);
        }finally {
            ringBuffer.publish(next);
        }
    }

    // -=============================第二种用法：使用synchronized方法初始化disruptor-=================
    /**
     *  Creates and starts a new Disruptor and associated thread if none currently exists.
     */
    public synchronized void start(){
        if(disruptor != null){
            return;
        }
        ringBufferSize = DisruptorUtil.calculateRingBufferSize("ringBufferSize");
        final WaitStrategy waitStrategy = DisruptorUtil.createWaitStrategy("BUSSYSPIN",10);

        // Executor 或者 ThreadFactory均可
        //final ExecutorService CACHED_THREAD_POOL = Executors.newCachedThreadPool();
        final ThreadFactory threadFactory = DaemonThreadFactory.INSTANCE;

        disruptor = new Disruptor<>(MsgEvent.FACTORY,ringBufferSize,threadFactory, ProducerType.MULTI,
                waitStrategy);

        final ExceptionHandler<MsgEvent> errorHandler = new MsgEventDefaultExceptionHandler();
        disruptor.setDefaultExceptionHandler(errorHandler);

        final MsgEventHandler[] handlers = {new MsgEventHandler()};
        disruptor.handleEventsWith(handlers);

        disruptor.start();
    }
    // 参考Log4j2 AsyncLoggerDisruptor的stop实现
    // 主要在shutdown之前做一些清理等待操作
    // stop()

    public synchronized void shutDown(){
        if(disruptor == null){
            return;
        }
        try {
            // busy-spins until all events currently in the disruptor have been processed, or timeout
            disruptor.shutdown(1000, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            disruptor.halt(); // give up on remaining msg , if any
        }finally {
            disruptor = null;
        }
    }



    //-=======================第二种方法：使用EventTranslator来publish消息=---====================
    /**
     * 尝试提交生产者生产的消息
     * @param translator 已经setValues后的translator
     * @return 是否生产成功
     */
    public boolean tryPublish(final MsgEventTranslator translator){
        try {
            return disruptor.getRingBuffer().tryPublishEvent(translator);
        }catch (final NullPointerException npe){
            return false;
        }
    }

    // 阻塞提交生产者生产的消息
    public void publish(final MsgEventTranslator translator){
        try {
            disruptor.publishEvent(translator);
        }catch (final NullPointerException npe){
            // do nothing
        }
    }

}

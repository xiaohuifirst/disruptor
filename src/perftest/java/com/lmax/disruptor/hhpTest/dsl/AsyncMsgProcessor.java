package com.lmax.disruptor.hhpTest.dsl;

import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.hhpTest.support.MsgEventTranslator;


/**
 * 生产者用来生产消息的类
 */
public class AsyncMsgProcessor {

    private final ThreadLocal<MsgEventTranslator> threadLocalTranslator = new ThreadLocal<>();
    // 类特有的一个disruptor对象
    private final static AsyncMsgProcessorDisruptor msgProcessorDisruptor = new AsyncMsgProcessorDisruptor();

    public AsyncMsgProcessor(){
        msgProcessorDisruptor.start();
    }

    public static void shutdownDisruptor(){
        msgProcessorDisruptor.shutDown();
    }

    // 生产消息
    public void produceMessage(final String sessionId, final String hopByhop, final String originRealm){

        final MsgEventTranslator translator = getCachedTranslator();
        translator.setBasicValues(this,sessionId,hopByhop,originRealm);
        publish(translator);
    }

    private void publish(final MsgEventTranslator translator){
//        // 尝试生产失败时
//        if(!msgProcessorDisruptor.tryPublish(translator)){
//            handleRingBufferFull(translator);
//        }
        // 直接生产
        msgProcessorDisruptor.publish(translator);
    }

    // ringbuffer空间不足：消费者消费慢了
    private void handleRingBufferFull(final MsgEventTranslator translator){
        // 要么丢弃，要么等待
        // 丢弃
        if(false){
            // do nothing
        }else{
            // 阻塞生产
            msgProcessorDisruptor.publish(translator);
        }
    }

    // 多线程生产时，使用线程局部Translator对象
    private MsgEventTranslator getCachedTranslator(){
        MsgEventTranslator result = threadLocalTranslator.get();
        if(result == null){
            result = new MsgEventTranslator();
            threadLocalTranslator.set(result);
        }
        return result;
    }
}

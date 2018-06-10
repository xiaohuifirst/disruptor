package com.lmax.disruptor.hhpTest.support;

import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.hhpTest.dsl.AsyncMsgProcessor;

/**
 * 生产者用来publishEvent的类
 */
public class MsgEventTranslator implements
        EventTranslator<MsgEvent> {

    private AsyncMsgProcessor asyncMsgProcessor;
    private String sessionId;
    private long recvTime;
    private String hopByhop;
    private String originRealm;

    @Override
    public void translateTo(MsgEvent event, long sequence) {
        // modify the event
        event.setValues(asyncMsgProcessor,sessionId,recvTime,hopByhop,originRealm);
        // clear the translator
        clear();
    }

    private void clear(){
        setBasicValues(null,null,null,null);
    }

    public void setBasicValues(final AsyncMsgProcessor msgProcessor,
                               final String sessionId, final String hopByhop,
                               final String originRealm) {
        this.asyncMsgProcessor = msgProcessor;
        this.sessionId = sessionId;
        this.recvTime = System.currentTimeMillis();
//        this.recvTime = 100233;
        this.hopByhop = hopByhop;
        this.originRealm = originRealm;
    }
}

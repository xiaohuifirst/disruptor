package com.lmax.disruptor.hhpTest.support;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.hhpTest.dsl.AsyncMsgProcessor;

public final class MsgEvent {
    private String sessionId;
    private long recvTime;
    private String hopByhop;
    private String originRealm;

    private transient AsyncMsgProcessor asyncMsgProcessor;
    // ....

    // publish使用
    public void setValues(final AsyncMsgProcessor asyncMsgProcessor,
                          final String sessionId, final long recvTime,
                          final String hopByhop, final String originRealm) {
        this.asyncMsgProcessor = asyncMsgProcessor;
        this.sessionId = sessionId;
        this.recvTime = recvTime;
        this.hopByhop = hopByhop;
        this.originRealm = originRealm;
    }

    // 清理使用
    public void clear(){
        // Release references held by ring buffer to allow objects to be garbage-collected.
        this.asyncMsgProcessor = null;
        sessionId = null;
        hopByhop = null;
        originRealm = null;
    }

    /**
     * Event processor that reads the event from the ringbuffer can call this method.
     *
     * 具体处理逻辑
     *
     * @param endOfBatch flag to indicate if this is the last event in a batch from the RingBuffer
     */
    public void execute(final boolean endOfBatch) {
        // demo
        sessionId = sessionId+hopByhop+originRealm+recvTime;
        recvTime ++;
    }

    public static final Factory FACTORY  = new Factory();

    public String getSessionId() {
        return sessionId;
    }

    public long getRecvTime() {
        return recvTime;
    }

    public String getHopByhop() {
        return hopByhop;
    }

    public String getOriginRealm() {
        return originRealm;
    }

    private static class Factory implements EventFactory<MsgEvent>{

        @Override
        public MsgEvent newInstance() {
            return new MsgEvent();
        }
    }
}

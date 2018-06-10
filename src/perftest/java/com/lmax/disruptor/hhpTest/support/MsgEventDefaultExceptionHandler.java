package com.lmax.disruptor.hhpTest.support;

import com.lmax.disruptor.ExceptionHandler;

public class MsgEventDefaultExceptionHandler implements ExceptionHandler<MsgEvent> {

    @Override
    public void handleEventException(Throwable ex, long sequence, MsgEvent event) {
        System.err.println(event.getSessionId());
        ex.printStackTrace();
    }

    @Override
    public void handleOnStartException(Throwable ex) {
        System.err.println("AsyncMsgProcessor error starting:");
        ex.printStackTrace();
    }

    @Override
    public void handleOnShutdownException(Throwable ex) {
        System.err.println("AsyncMsgProcessor error shutting down:");
        ex.printStackTrace();
    }
}

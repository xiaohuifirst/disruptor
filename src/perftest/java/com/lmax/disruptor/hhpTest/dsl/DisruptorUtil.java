package com.lmax.disruptor.hhpTest.dsl;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.TimeoutBlockingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;

import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class DisruptorUtil {

    private static final int RINGBUFFER_MIN_SIZE = 1024*64;
    private static final int RINGBUFFER_DEFAULT_SIZE = 256*1024;
    private static final int RINGBUFFER_NO_GC_DEFAULT_SIZE = 4*1024;
    private static final Properties confProp = new Properties();

    private DisruptorUtil(){}

    static long getTimeout(final String propertyName, final long defaultTimeout){
        //return Long.parseLong(confProp.getProperty(propertyName,defaultTimeout));
        return defaultTimeout;
    }

    static WaitStrategy createWaitStrategy(final String propertyName){
        final String key = confProp.getProperty(propertyName,"TIMEOUT");
        // 10ms，很长了
        final long timeoutMillis = DisruptorUtil.getTimeout(key,10L);
        return createWaitStrategy(key,timeoutMillis);
    }

    static WaitStrategy createWaitStrategy(final String strategyType, final long timeoutMillis){
        final String strategyUp = strategyType.toUpperCase(Locale.ROOT);
        switch (strategyType) {      // TODO Define a DisruptorWaitStrategy enum?
            case "SLEEP":
                return new SleepingWaitStrategy();
            case "YIELD":
                return new YieldingWaitStrategy();
            case "BLOCK":
                return new BlockingWaitStrategy();
            case "BUSSYSPIN":
                return new BusySpinWaitStrategy();
            case "TIMEOUT":
                return new TimeoutBlockingWaitStrategy(timeoutMillis, TimeUnit.MILLISECONDS);
            default:
                return new TimeoutBlockingWaitStrategy(timeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    static int calculateRingBufferSize(final String propertyName){
        int size = Integer.parseInt(confProp.getProperty(propertyName,"64"));
        if(size<RINGBUFFER_MIN_SIZE){
            size = RINGBUFFER_MIN_SIZE;
        }
        // 参考log4j2的DisruptorUtil来实现
        return size;
    }


}

/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import com.lmax.disruptor.util.ThreadHints;

/**
 * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
 * <p>
 * This strategy can be used when throughput and low-latency are not as important as CPU resource.
 * BlockingWaitStrategy是一种利用锁和等待机制的WaitStrategy，CPU消耗少，但是延迟比较高。
 * 原理是，所有消费者首先查是否Alert，如果是，则抛AlertException从等待中返回。之后检查sequence是否已经被发布，
 * 就是当前cursorSequence是否大于想消费的sequence。若不满足，mutex.wait()等待。
 */
public final class BlockingWaitStrategy implements WaitStrategy
{
    // wait notify,也可用通过lock+condition来实现
    private final Object mutex = new Object();

    @Override
    public long waitFor(long sequence, Sequence cursorSequence, Sequence dependentSequence, SequenceBarrier barrier)
        throws AlertException, InterruptedException
    {
        long availableSequence;

        if (cursorSequence.get() < sequence)
        {
            // 加锁
            synchronized (mutex)
            {
                // 要消费的位置比生产者生产的还早
                // 由于生产者在生产Event之后会调用signalAllWhenBlocking()唤醒等待，让所有消费者重新检查。
                // 所以，这里只先检查cursorSequence.get() < sequence而不是dependentSequence.get() < sequence.
                while (cursorSequence.get() < sequence)
                {
                    // 检查是否Alert，如果Alert，则抛出AlertException
                    // Alert在这里代表对应的消费者被halt停止了
                    barrier.checkAlert();
                    // 在mutex上等待唤醒
                    mutex.wait();
                }
            }
        }

        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            barrier.checkAlert();
            ThreadHints.onSpinWait();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
        synchronized (mutex)
        {
            // 生产者生产消息后，会唤醒所有等待的消费者线程
            mutex.notifyAll();
        }
    }

    @Override
    public String toString()
    {
        return "BlockingWaitStrategy{" +
            "mutex=" + mutex +
            '}';
    }
}

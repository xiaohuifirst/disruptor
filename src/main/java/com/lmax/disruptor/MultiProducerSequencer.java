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

import java.util.concurrent.locks.LockSupport;

import sun.misc.Unsafe;

import com.lmax.disruptor.util.Util;


/**
 * <p>Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s.
 * Suitable for use for sequencing across multiple publisher threads.</p>
 *
 * <p> * Note on {@link Sequencer#getCursor()}:  With this sequencer the cursor value is updated after the call
 * to {@link Sequencer#next()}, to determine the highest available sequence that can be read, then
 * {@link Sequencer#getHighestPublishedSequence(long, long)} should be used.</p>
 */
public final class MultiProducerSequencer extends AbstractSequencer
{
    private static final Unsafe UNSAFE = Util.getUnsafe();
    // 数组首元素地址
    private static final long BASE = UNSAFE.arrayBaseOffset(int[].class);
    // 步长
    private static final long SCALE = UNSAFE.arrayIndexScale(int[].class);

    // gatingSequence的缓存，和之前的单一生产者的类似
    private final Sequence gatingSequenceCache = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

    // availableBuffer tracks the state of each ringbuffer slot
    // see below for more details on the approach
    // 每个槽存过几个Event，就是sequence到底转了多少圈，存在这个数组里，下标就是每个槽。
    // 为什么不直接将sequence存入availableBuffer，因为这样sequence值会过大，很容易溢出.
    private final int[] availableBuffer;
    // 利用对2^n取模 = 对2^n -1 取与运算原理，indexMask=bufferSize - 1
    private final int indexMask;
    // 就是上面的n，用来定位某个sequence到底转了多少圈，用来标识已被发布的sequence。
    private final int indexShift;

    /**
     * Construct a Sequencer with the selected wait strategy and buffer size.
     *
     * @param bufferSize   the size of the buffer that this will sequence over.
     * @param waitStrategy for those waiting on sequences.
     */
    public MultiProducerSequencer(int bufferSize, final WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
        availableBuffer = new int[bufferSize];
        indexMask = bufferSize - 1;
        // 对2取对数
        indexShift = Util.log2(bufferSize);
        initialiseAvailableBuffer();
    }

    /**
     * @see Sequencer#hasAvailableCapacity(int)
     */
    @Override
    public boolean hasAvailableCapacity(final int requiredCapacity)
    {
        return hasAvailableCapacity(gatingSequences, requiredCapacity, cursor.get());
    }

    private boolean hasAvailableCapacity(Sequence[] gatingSequences, final int requiredCapacity, long cursorValue)
    {
        // 下一位置加上所需容量减去整个bufferSize，如果为正数，就证明至少转了一圈
        // 那么需要检查gatingSequences(由消费者更新里面的Sequence值)以保证不覆盖还未被消费的消息
        // 由于最多只能生产不大于整个bufferSize的Events，所以减去一个bufferSize与最小的sequence比较即可
        long wrapPoint = (cursorValue + requiredCapacity) - bufferSize;
        // 读取缓存的值
        long cachedGatingSequence = gatingSequenceCache.get();

        // 缓存失效的条件（见SingleProducerSequencer）
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > cursorValue)
        {
            long minSequence = Util.getMinimumSequence(gatingSequences, cursorValue);
            gatingSequenceCache.set(minSequence);

            // 空间不足
            if (wrapPoint > minSequence)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * @see Sequencer#claim(long)
     */
    @Override
    public void claim(long sequence)
    {
        cursor.set(sequence);
    }

    /**
     * @see Sequencer#next()
     */
    @Override
    public long next()
    {
        return next(1);
    }

    /**
     * @see Sequencer#next(int)
     * 用于多个生产者枪战n个RingBuffer的槽slot，用于生产Event.
     */
    @Override
    public long next(int n)
    {
        if (n < 1)
        {
            throw new IllegalArgumentException("n must be > 0");
        }

        long current;
        long next;

        do
        {
            // 首先根据缓存判断空间是否足够
            current = cursor.get();
            next = current + n;

            long wrapPoint = next - bufferSize;
            long cachedGatingSequence = gatingSequenceCache.get();

            // 如果缓存不满足
            if (wrapPoint > cachedGatingSequence || cachedGatingSequence > current)
            {
                // 重新获取最小的Sequence
                long gatingSequence = Util.getMinimumSequence(gatingSequences, current);

                // 如果空间不足，则唤醒消费者消费，并让出CPU
                if (wrapPoint > gatingSequence)
                {
                    // hhp added.
                    //waitStrategy.signalAllWhenBlocking();
                    LockSupport.parkNanos(1); // TODO, should we spin based on the wait strategy?
                    continue;
                }

                // 更新缓存值
                gatingSequenceCache.set(gatingSequence);
            }   // 如果空间足够，尝试CAS更新cursor，更新cursor成功代表成功获取到了n个槽，退出循环
            // 否则继续进行循环CAS
            else if (cursor.compareAndSet(current, next))
            {
                break;
            }
        }
        while (true);

        // 返回最新的cursor值
        return next;
    }

    /**
     * @see Sequencer#tryNext()
     */
    @Override
    public long tryNext() throws InsufficientCapacityException
    {
        return tryNext(1);
    }

    /**
     * @see Sequencer#tryNext(int)
     * 多个生产者尝试抢占n个槽
     */
    @Override
    public long tryNext(int n) throws InsufficientCapacityException
    {
        if (n < 1)
        {
            throw new IllegalArgumentException("n must be > 0");
        }

        long current;
        long next;

        // 尝试获取一次，若不成功，则抛出InsufficientCapacityException
        do
        {
            current = cursor.get();
            next = current + n;

            if (!hasAvailableCapacity(gatingSequences, n, current))
            {
                // 跳出循环
                throw InsufficientCapacityException.INSTANCE;
            }
        }
        while (!cursor.compareAndSet(current, next));

        return next;
    }

    /**
     * @see Sequencer#remainingCapacity()
     */
    @Override
    public long remainingCapacity()
    {
        long consumed = Util.getMinimumSequence(gatingSequences, cursor.get());
        long produced = cursor.get();
        return getBufferSize() - (produced - consumed);
    }

    private void initialiseAvailableBuffer()
    {
        for (int i = availableBuffer.length - 1; i != 0; i--)
        {
            setAvailableBufferValue(i, -1);
        }

        setAvailableBufferValue(0, -1);
    }

    /**
     * @see Sequencer#publish(long)
     * 配置好Event后，发布public该sequence
     */
    @Override
    public void publish(final long sequence)
    {
        setAvailable(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * @see Sequencer#publish(long, long)
     */
    @Override
    public void publish(long lo, long hi)
    {
        for (long l = lo; l <= hi; l++)
        {
            setAvailable(l);
        }
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * The below methods work on the availableBuffer flag.
     * <p>
     * The prime reason is to avoid a shared sequence object between publisher threads.
     * (Keeping single pointers tracking start and end would require coordination
     * between the threads).
     * <p>
     * --  Firstly we have the constraint that the delta between the cursor and minimum
     * gating sequence will never be larger than the buffer size (the code in
     * next/tryNext in the Sequence takes care of that).
     * -- Given that; take the sequence value and mask off the lower portion of the
     * sequence as the index into the buffer (indexMask). (aka modulo operator)
     * -- The upper portion of the sequence becomes the value to check for availability.
     * ie: it tells us how many times around the ring buffer we've been (aka division)
     * -- Because we can't wrap(缠绕) without the gating sequences moving forward (i.e. the
     * minimum gating sequence is effectively our last available position in the
     * buffer), when we have new data and successfully claimed a slot we can simply
     * write over the top.
     * 最后一句意思：当我们成功的为一个新的数据申请到槽以后，我们可以简单的在上面写！
     * 原因1：我们限制了在最小的gatingSequence和cursor之间的差不能大于bufferSize（next函数保证了这一点）
     * 原因2：publish时，计算出该sequence对应槽的位置和Flag（转了多少圈）之后，由于在next和publish中间，
     * 我们不能越过最小的gatingSequences来再次申请这几个槽，所以这次写入这几个槽是安全的！！
     *
     * 发布某个sequence之前的都可以被消费了
     * 这里需要将availableBuffer上对应sequence下标的值设置为第几次用到这个槽
     */
    private void setAvailable(final long sequence)
    {
        setAvailableBufferValue(calculateIndex(sequence), calculateAvailabilityFlag(sequence));
    }

    private void setAvailableBufferValue(int index, int flag)
    {
        // 每个数组值的地址：Base + Scale*下标
        long bufferAddress = (index * SCALE) + BASE;
        UNSAFE.putOrderedInt(availableBuffer, bufferAddress, flag);
    }

    /**
     * @see Sequencer#isAvailable(long)
     */
    @Override
    public boolean isAvailable(long sequence)
    {
        int index = calculateIndex(sequence);
        int flag = calculateAvailabilityFlag(sequence);
        long bufferAddress = (index * SCALE) + BASE;
        // 判断该sequence号应该在的槽的上面的flag是否与计算出来的值相等（即这个号还没被转一圈覆盖掉）
        return UNSAFE.getIntVolatile(availableBuffer, bufferAddress) == flag;
    }

    @Override
    public long getHighestPublishedSequence(long lowerBound, long availableSequence)
    {
        for (long sequence = lowerBound; sequence <= availableSequence; sequence++)
        {
            if (!isAvailable(sequence))
            {
                return sequence - 1;
            }
        }

        return availableSequence;
    }

    //某个sequence右移indexShift，代表这个Sequence是第几次用到这个ringBuffer的某个槽
    // 也就是这个sequence转了多少圈
    private int calculateAvailabilityFlag(final long sequence)
    {
        // 无符号右移，忽略符号位，空位都以0补齐
        // 右移n次以后，得到的值是sequence/2^n，即转过了多少圈
        return (int) (sequence >>> indexShift);
    }

    // 计算槽的位置
    // 定位ringBuffer上某个槽用于生产event，对2^n取模 = 对2^n -1
    private int calculateIndex(final long sequence)
    {
        return ((int) sequence) & indexMask;
    }
}

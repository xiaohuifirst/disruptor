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


/**
 * {@link SequenceBarrier} handed out for gating {@link EventProcessor}s on a cursor sequence and optional dependent {@link EventProcessor}(s),
 * using the given WaitStrategy.
 * 构造SequenceBarrier在框架中只有一个入口，就是{@link AbstractSequencer#newBarrier}接口
 */
final class ProcessingSequenceBarrier implements SequenceBarrier
{
    private final WaitStrategy waitStrategy;
    private final Sequence dependentSequence;
    private volatile boolean alerted = false;
    private final Sequence cursorSequence;
    private final Sequencer sequencer;

    ProcessingSequenceBarrier(
        final Sequencer sequencer,
        final WaitStrategy waitStrategy,
        final Sequence cursorSequence,
        final Sequence[] dependentSequences)
    {
        // 生产者Sequence
        this.sequencer = sequencer;
        // 等待策略
        this.waitStrategy = waitStrategy;
        // 消费者定位
        this.cursorSequence = cursorSequence;
        // 一组依赖sequence
        if (0 == dependentSequences.length)
        {
            dependentSequence = cursorSequence;
        }
        else
        {
            dependentSequence = new FixedSequenceGroup(dependentSequences);
        }
    }

    @Override
    public long waitFor(final long sequence)
        throws AlertException, InterruptedException, TimeoutException
    {
        // 检查是否alerted
        checkAlert();

        // 通过等待策略获取下一个可消费的sequence，这个sequence需要大于sequence，小于cursorSequence和dependentSequence
        // 我们可以通过dependentSequence实现先后消费
        long availableSequence = waitStrategy.waitFor(sequence, cursorSequence, dependentSequence, this);

        // 等待可能被中断，所以检查availableSequence是否小于sequence
        if (availableSequence < sequence)
        {
            return availableSequence;
        }

        // 如果不小于，返回sequence（可能多生产者）和availableSequence中最大的
        //获取消费者可以消费的最大的可用序号，支持批处理效应，提升处理效率。
        return sequencer.getHighestPublishedSequence(sequence, availableSequence);
    }

    // 获取当前cursorSequence（并没有什么用，就是为了监控）
    @Override
    public long getCursor()
    {
        return dependentSequence.get();
    }

    // 负责中断和恢复的alert标记
    @Override
    public boolean isAlerted()
    {
        return alerted;
    }

    @Override
    public void alert()
    {
        alerted = true;
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void clearAlert()
    {
        alerted = false;
    }

    @Override
    public void checkAlert() throws AlertException
    {
        if (alerted)
        {
            throw AlertException.INSTANCE;
        }
    }
}
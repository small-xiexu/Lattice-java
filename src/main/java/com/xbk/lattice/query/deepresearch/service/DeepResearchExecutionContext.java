package com.xbk.lattice.query.deepresearch.service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deep Research 执行上下文
 *
 * 职责：维护单次深度研究的预算、超时与内部证据序号
 *
 * @author xiexu
 */
public class DeepResearchExecutionContext {

    private final int maxLlmCalls;

    private final long deadlineEpochMs;

    private final AtomicInteger llmCallCount = new AtomicInteger(0);

    private final AtomicInteger evidenceSequence = new AtomicInteger(0);

    private final AtomicBoolean timedOut = new AtomicBoolean(false);

    /**
     * 创建 Deep Research 执行上下文。
     *
     * @param maxLlmCalls 最大 LLM 调用次数
     * @param deadlineEpochMs 截止时间
     */
    public DeepResearchExecutionContext(int maxLlmCalls, long deadlineEpochMs) {
        this.maxLlmCalls = maxLlmCalls;
        this.deadlineEpochMs = deadlineEpochMs;
    }

    /**
     * 尝试占用一次 LLM 调用配额。
     *
     * @return 是否占用成功
     */
    public boolean tryAcquireLlmCall() {
        if (isTimedOut()) {
            return false;
        }
        while (true) {
            int current = llmCallCount.get();
            if (current >= maxLlmCalls) {
                return false;
            }
            if (llmCallCount.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    /**
     * 返回是否已超时。
     *
     * @return 是否已超时
     */
    public boolean isTimedOut() {
        if (timedOut.get()) {
            return true;
        }
        if (deadlineEpochMs > 0L && System.currentTimeMillis() > deadlineEpochMs) {
            timedOut.set(true);
            return true;
        }
        return false;
    }

    /**
     * 返回剩余 LLM 调用预算。
     *
     * @return 剩余 LLM 调用预算
     */
    public int remainingLlmCalls() {
        int remaining = maxLlmCalls - llmCallCount.get();
        return Math.max(remaining, 0);
    }

    /**
     * 分配下一个证据号。
     *
     * @return 证据号
     */
    public String nextEvidenceId() {
        return "ev#" + evidenceSequence.incrementAndGet();
    }

    /**
     * 返回已消耗的 LLM 调用次数。
     *
     * @return 已消耗的 LLM 调用次数
     */
    public int llmCallCount() {
        return llmCallCount.get();
    }
}

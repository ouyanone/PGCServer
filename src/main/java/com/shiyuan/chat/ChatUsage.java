package com.shiyuan.chat;

import java.math.BigDecimal;

/**
 * What one question has cost so far. The caller owns it, so spend is recorded even when a
 * later step of the tool loop fails.
 */
public final class ChatUsage {

    private long inputTokens;
    private long outputTokens;
    private BigDecimal cost = BigDecimal.ZERO;

    public void add(long in, long out, BigDecimal stepCost) {
        inputTokens += in;
        outputTokens += out;
        cost = cost.add(stepCost);
    }

    public long inputTokens() { return inputTokens; }
    public long outputTokens() { return outputTokens; }
    public BigDecimal cost() { return cost; }
}

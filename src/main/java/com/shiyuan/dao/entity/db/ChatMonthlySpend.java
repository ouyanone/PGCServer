package com.shiyuan.dao.entity.db;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Model spend for one New York calendar month ('YYYY-MM'). Added to only by a native upsert. */
@Entity
@Table(name = "chat_monthly_spend")
public class ChatMonthlySpend {

    @Id
    @Column(name = "month")
    private String month;

    @Column(name = "cost_usd", nullable = false, precision = 10, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public BigDecimal getCostUsd() { return costUsd; }
    public void setCostUsd(BigDecimal costUsd) { this.costUsd = costUsd; }
    public long getInputTokens() { return inputTokens; }
    public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
}

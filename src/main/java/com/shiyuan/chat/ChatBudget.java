package com.shiyuan.chat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.repository.ChatMonthlySpendRepository;

/** The monthly model budget ($10 by default), counted per New York calendar month. */
@Service
public class ChatBudget {

    private final ChatMonthlySpendRepository repo;
    private final Clock clock;
    private final BigDecimal monthlyLimit;

    @Autowired
    public ChatBudget(ChatMonthlySpendRepository repo, Clock chatClock,
                      @Value("${pgc.chat.monthly-budget-usd:10.00}") BigDecimal monthlyLimit) {
        this.repo = repo;
        this.clock = chatClock;
        this.monthlyLimit = monthlyLimit;
    }

    @Transactional(readOnly = true)
    public boolean exhausted() {
        BigDecimal spent = repo.findById(month()).map(s -> s.getCostUsd()).orElse(BigDecimal.ZERO);
        return spent.compareTo(monthlyLimit) >= 0;
    }

    @Transactional
    public void record(BigDecimal cost, long inputTokens, long outputTokens) {
        repo.add(month(), cost, inputTokens, outputTokens);
    }

    private String month() {
        return YearMonth.now(clock).toString();
    }
}

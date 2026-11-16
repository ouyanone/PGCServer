package com.shiyuan.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.shiyuan.dao.entity.db.ChatMonthlySpend;
import com.shiyuan.dao.repository.ChatMonthlySpendRepository;

class ChatBudgetTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private final ChatMonthlySpendRepository repo = mock(ChatMonthlySpendRepository.class);

    private ChatBudget at(String instant) {
        return new ChatBudget(repo, Clock.fixed(Instant.parse(instant), NY), new BigDecimal("10.00"));
    }

    private void spent(String month, String usd) {
        ChatMonthlySpend s = new ChatMonthlySpend();
        s.setMonth(month);
        s.setCostUsd(new BigDecimal(usd));
        when(repo.findById(month)).thenReturn(Optional.of(s));
    }

    @Test
    void exhausted_trueAtExactlyTen() {
        spent("2026-11", "10.000000");
        assertTrue(at("2026-11-15T15:00:00Z").exhausted());
    }

    @Test
    void exhausted_falseJustUnderTen() {
        spent("2026-11", "9.999999");
        assertFalse(at("2026-11-15T15:00:00Z").exhausted());
    }

    @Test
    void exhausted_falseWithNoSpendYet() {
        when(repo.findById("2026-11")).thenReturn(Optional.empty());
        assertFalse(at("2026-11-15T15:00:00Z").exhausted());
    }

    /** 03:00 UTC on December 1st is still November 30th in New Jersey. */
    @Test
    void month_usesNewYorkZone() {
        spent("2026-11", "10.00");
        assertTrue(at("2026-12-01T03:00:00Z").exhausted());
    }

    @Test
    void record_addsToThisMonth() {
        at("2026-11-15T15:00:00Z").record(new BigDecimal("0.000123"), 100, 20);
        verify(repo).add("2026-11", new BigDecimal("0.000123"), 100L, 20L);
    }
}

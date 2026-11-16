package com.shiyuan.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.shiyuan.dao.repository.ChatDailyCountRepository;

class ChatLimitsTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    private final ChatDailyCountRepository repo = mock(ChatDailyCountRepository.class);

    private ChatLimits at(String instant) {
        return new ChatLimits(repo, Clock.fixed(Instant.parse(instant), NY));
    }

    @Test
    void tryConsume_trueUpToThirty() {
        LocalDate day = LocalDate.of(2026, 11, 15);
        when(repo.questionsOn("sub-A", day)).thenReturn(30);
        assertTrue(at("2026-11-15T15:00:00Z").tryConsume("sub-A"));
        verify(repo).increment("sub-A", day);
    }

    @Test
    void tryConsume_falseOnThirtyFirst() {
        when(repo.questionsOn("sub-A", LocalDate.of(2026, 11, 15))).thenReturn(31);
        assertFalse(at("2026-11-15T15:00:00Z").tryConsume("sub-A"));
    }

    /** 03:30 UTC on the 16th is still the evening of the 15th in New Jersey. */
    @Test
    void tryConsume_usesNewYorkDay() {
        LocalDate day = LocalDate.of(2026, 11, 15);
        when(repo.questionsOn("sub-A", day)).thenReturn(1);
        assertTrue(at("2026-11-16T03:30:00Z").tryConsume("sub-A"));
        verify(repo).increment("sub-A", day);
    }

    @Test
    void refund_decrementsTodayInNewYork() {
        at("2026-11-16T03:30:00Z").refund("sub-A");
        verify(repo).decrement("sub-A", LocalDate.of(2026, 11, 15));
    }
}

package com.shiyuan.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shiyuan.dao.entity.db.ChatExchange;
import com.shiyuan.dao.repository.ChatExchangeRepository;

class ChatHistoryServiceTest {

    private final ChatExchangeRepository repo = mock(ChatExchangeRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneId.of("America/New_York"));
    private final ChatHistoryService history = new ChatHistoryService(repo, clock);

    private static ChatExchange row(long id, String q, String at) {
        ChatExchange e = new ChatExchange();
        e.setId(id);
        e.setMemberSub("sub-A");
        e.setQuestion(q);
        e.setAnswer("answer to " + q);
        e.setCreatedAt(Instant.parse(at));
        return e;
    }

    @Test
    void save_trimsToNewestThree() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findTop3ByMemberSubOrderByCreatedAtDescIdDesc("sub-A")).thenReturn(List.of(
                row(9, "q9", "2026-11-15T15:00:00Z"),
                row(8, "q8", "2026-11-15T14:00:00Z"),
                row(7, "q7", "2026-11-15T13:00:00Z")));

        history.save("sub-A", "q9", "a9");

        verify(repo).deleteByMemberSubAndIdNotIn(eq("sub-A"), eq(List.of(9L, 8L, 7L)));
    }

    @Test
    void save_stampsTheClockTime() {
        when(repo.save(any())).thenAnswer(inv -> {
            ChatExchange e = inv.getArgument(0);
            assertEquals(Instant.parse("2026-11-15T15:00:00Z"), e.getCreatedAt());
            assertEquals("sub-A", e.getMemberSub());
            return e;
        });
        when(repo.findTop3ByMemberSubOrderByCreatedAtDescIdDesc("sub-A")).thenReturn(List.of());
        history.save("sub-A", "q", "a");
        verify(repo).save(any());
    }

    @Test
    void recent_isOldestFirstAndAsksOnlyForThisMember() {
        when(repo.findTop3ByMemberSubOrderByCreatedAtDescIdDesc("sub-A")).thenReturn(List.of(
                row(9, "q9", "2026-11-15T15:00:00Z"),
                row(8, "q8", "2026-11-15T14:00:00Z")));

        List<ChatExchangeDto> got = history.recent("sub-A");

        assertEquals(List.of("q8", "q9"), got.stream().map(ChatExchangeDto::question).toList());
        assertEquals("answer to q8", got.get(0).answer());
        assertEquals(Instant.parse("2026-11-15T14:00:00Z"), got.get(0).at());
    }
}

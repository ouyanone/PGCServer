package com.shiyuan.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.db.ChatExchange;
import com.shiyuan.dao.repository.ChatDailyCountRepository;
import com.shiyuan.dao.repository.ChatExchangeRepository;
import com.shiyuan.dao.repository.ChatMonthlySpendRepository;

/**
 * The SQL itself, against the local MySQL that ShiyuanApplicationTests already needs (tables
 * from src/main/resources/ddl/chat.sql). Each test runs in a transaction that is rolled back.
 * Mocks cannot prove an upsert, a floor at zero, or decimal addition -- the database does those.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("local")
class ChatRepositoriesTest {

    private static final LocalDate DAY = LocalDate.of(2001, 1, 1);
    private static final String MONTH = "2001-01";

    @Autowired ChatDailyCountRepository daily;
    @Autowired ChatMonthlySpendRepository spend;
    @Autowired ChatExchangeRepository exchanges;

    @Test
    void increment_isAnUpsert() {
        daily.increment("test-sub", DAY);
        daily.increment("test-sub", DAY);
        assertEquals(2, daily.questionsOn("test-sub", DAY));
    }

    @Test
    void decrement_neverBelowZero() {
        daily.increment("test-sub", DAY);
        daily.decrement("test-sub", DAY);
        daily.decrement("test-sub", DAY);
        assertEquals(0, daily.questionsOn("test-sub", DAY));
    }

    @Test
    void add_sumsDecimalsWithoutLoss() {
        spend.add(MONTH, new BigDecimal("0.000123"), 100L, 20L);
        spend.add(MONTH, new BigDecimal("0.000456"), 50L, 5L);
        spend.flush();
        var row = spend.findById(MONTH).orElseThrow();
        assertEquals(0, new BigDecimal("0.000579").compareTo(row.getCostUsd()), row.getCostUsd().toPlainString());
        assertEquals(150L, row.getInputTokens());
        assertEquals(25L, row.getOutputTokens());
    }

    @Test
    void trim_deletesOnlyThisMembersOlderRows() {
        for (int i = 1; i <= 4; i++) {
            exchanges.save(exchange("test-A", "a" + i, i));
        }
        ChatExchange other = exchanges.save(exchange("test-B", "b1", 0));

        List<Long> keep = exchanges.findTop3ByMemberSubOrderByCreatedAtDescIdDesc("test-A")
                .stream().map(ChatExchange::getId).toList();
        exchanges.deleteByMemberSubAndIdNotIn("test-A", keep);

        assertEquals(List.of("a4", "a3", "a2"), exchanges.findTop3ByMemberSubOrderByCreatedAtDescIdDesc("test-A")
                .stream().map(ChatExchange::getQuestion).toList());
        assertEquals(List.of(other.getId()), exchanges.findTop3ByMemberSubOrderByCreatedAtDescIdDesc("test-B")
                .stream().map(ChatExchange::getId).toList());
    }

    private static ChatExchange exchange(String sub, String q, int minute) {
        ChatExchange e = new ChatExchange();
        e.setMemberSub(sub);
        e.setQuestion(q);
        e.setAnswer("x");
        e.setCreatedAt(Instant.parse("2001-01-01T00:00:00Z").plusSeconds(60L * minute));
        return e;
    }
}

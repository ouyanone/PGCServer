package com.shiyuan.dao.repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shiyuan.dao.entity.db.ChatMonthlySpend;

public interface ChatMonthlySpendRepository extends JpaRepository<ChatMonthlySpend, String> {

    /** Adds in the database, in DECIMAL, in one statement. */
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = "INSERT INTO chat_monthly_spend (month, cost_usd, input_tokens, output_tokens) "
            + "VALUES (:month, :cost, :inTok, :outTok) ON DUPLICATE KEY UPDATE "
            + "cost_usd = cost_usd + VALUES(cost_usd), "
            + "input_tokens = input_tokens + VALUES(input_tokens), "
            + "output_tokens = output_tokens + VALUES(output_tokens)")
    void add(@Param("month") String month, @Param("cost") BigDecimal cost,
             @Param("inTok") long inputTokens, @Param("outTok") long outputTokens);
}

package com.shiyuan.dao.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shiyuan.dao.entity.db.ChatDailyCount;

public interface ChatDailyCountRepository extends JpaRepository<ChatDailyCount, ChatDailyCount.Key> {

    /** One statement, so two questions sent at once both count (no read-then-write race). */
    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO chat_daily_count (member_sub, day, questions) "
            + "VALUES (:sub, :day, 1) ON DUPLICATE KEY UPDATE questions = questions + 1")
    void increment(@Param("sub") String memberSub, @Param("day") LocalDate day);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE chat_daily_count SET questions = GREATEST(questions - 1, 0) "
            + "WHERE member_sub = :sub AND day = :day")
    void decrement(@Param("sub") String memberSub, @Param("day") LocalDate day);

    @Query(nativeQuery = true, value = "SELECT COALESCE(MAX(questions), 0) FROM chat_daily_count "
            + "WHERE member_sub = :sub AND day = :day")
    int questionsOn(@Param("sub") String memberSub, @Param("day") LocalDate day);
}

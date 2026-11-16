package com.shiyuan.dao.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shiyuan.dao.entity.db.ChatExchange;

public interface ChatExchangeRepository extends JpaRepository<ChatExchange, Long> {

    /** Newest first; id breaks a tie between two rows saved in the same millisecond. */
    List<ChatExchange> findTop3ByMemberSubOrderByCreatedAtDescIdDesc(String memberSub);

    @Modifying
    @Query("DELETE FROM ChatExchange e WHERE e.memberSub = :sub AND e.id NOT IN :keep")
    void deleteByMemberSubAndIdNotIn(@Param("sub") String memberSub, @Param("keep") List<Long> keep);
}

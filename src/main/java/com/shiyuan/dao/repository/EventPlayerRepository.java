package com.shiyuan.dao.repository;

import com.shiyuan.dao.entity.db.EventPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventPlayerRepository extends JpaRepository<EventPlayer, Long> {

    List<EventPlayer> findByEventId(Long eventId);

    boolean existsByEventIdAndPlayerId(Long eventId, Long playerId);

    void deleteByEventIdAndPlayerId(Long eventId, Long playerId);
}

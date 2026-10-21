package com.shiyuan.dao.repository;

import com.shiyuan.dao.entity.db.EventPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventPlayerRepository extends JpaRepository<EventPlayer, Long> {

    List<EventPlayer> findByEventId(Long eventId);

    boolean existsByEventIdAndPlayerId(Long eventId, Long playerId);

    void deleteByEventIdAndPlayerId(Long eventId, Long playerId);

    /** Roster rows are not cascaded from Event, so deleting an event must clear them first. */
    @Modifying
    @Query("DELETE FROM EventPlayer ep WHERE ep.event.id = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId);
}

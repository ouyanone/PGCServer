package com.shiyuan.dao.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.shiyuan.dao.entity.db.Tee;

import java.util.List;

public interface TeeRepository extends JpaRepository<Tee, Long> {

    List<Tee> findByEventIdOrderByTeeName(Long eventId);

    @Modifying
    @Query("DELETE FROM Tee t WHERE t.event.id = :eventId")
    void deleteByEventId(Long eventId);

    @Modifying
    @Query("DELETE FROM Tee t WHERE t.id = :id")
    void deleteTeeById(Long id);
}

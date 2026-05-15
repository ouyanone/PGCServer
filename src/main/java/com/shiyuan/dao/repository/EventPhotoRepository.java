package com.shiyuan.dao.repository;

import com.shiyuan.dao.entity.db.EventPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventPhotoRepository extends JpaRepository<EventPhoto, Long> {

    List<EventPhoto> findByEventIdOrderByUploadedAtAsc(Long eventId);

    @Query("SELECT DISTINCT ep.event.id FROM EventPhoto ep")
    List<Long> findDistinctEventIds();
}

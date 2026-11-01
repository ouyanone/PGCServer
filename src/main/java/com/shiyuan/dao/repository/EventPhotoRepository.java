package com.shiyuan.dao.repository;

import com.shiyuan.dao.entity.db.EventPhoto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventPhotoRepository extends JpaRepository<EventPhoto, Long> {

    List<EventPhoto> findByEventIdOrderByUploadedAtAsc(Long eventId);

    /**
     * Photos from the most recent events first, for the home carousel. Ordered by the event's
     * own date rather than upload time, so uploading an old event's photos late does not push
     * them to the front. Within an event, newest upload first; id breaks any remaining tie.
     * Limit via Pageable.
     */
    @Query("SELECT ep FROM EventPhoto ep "
         + "ORDER BY ep.event.eventDate DESC, ep.uploadedAt DESC, ep.id DESC")
    List<EventPhoto> findLatestByEventDate(Pageable pageable);

    @Query("SELECT DISTINCT ep.event.id FROM EventPhoto ep")
    List<Long> findDistinctEventIds();

    /** Photos are not cascaded from Event, so deleting an event must clear them first. */
    @Modifying
    @Query("DELETE FROM EventPhoto ep WHERE ep.event.id = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId);
}

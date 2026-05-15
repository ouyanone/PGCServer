package com.shiyuan.dao.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.shiyuan.dao.entity.db.Event;

@Repository
public interface EventRepository extends CrudRepository<Event, Long>{
	
	Event findById(long id);

	Iterable<Event> findAllByOrderByEventDateDesc();
	
	Event findFirstByStatusAndEventDateGreaterThanEqualOrderByEventDate(String status, LocalDate today);
	
	Iterable<Event> findFirst3ByStatusOrderByEventDateDesc(String status);

	Event findFirstByStatus(String string);

	@Query("SELECT e FROM Event e WHERE e.season.id = ?1 AND e.status IN ?2")
	List<Event> findBySeasonIdAndStatusIn(Long seasonId, List<String> statuses);
}

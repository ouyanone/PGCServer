package com.shiyuan.dao.repository;

import java.time.LocalDate;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.shiyuan.dao.entity.db.Event;

@Repository
public interface EventRepository extends CrudRepository<Event, Long>{
	
	Event findById(long id);

	Iterable<Event> findAllByOrderByEventDateDesc();
	
	Event findFirstByStatusAndEventDateGreaterThanEqualOrderByEventDate(String status, LocalDate today);
	
	Iterable<Event> findFirst3ByStatusOrderByEventDateDesc(String status);

}

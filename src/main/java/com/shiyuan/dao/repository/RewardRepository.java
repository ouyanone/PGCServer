package com.shiyuan.dao.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shiyuan.dao.entity.db.Reward;

@Repository
public interface RewardRepository extends CrudRepository<Reward, Long>{

	
	
    @Query("SELECT r FROM Reward r WHERE r.event.id = :eventId order by r.displayOrder")
    List<Reward> findRewardsByEventId(@Param("eventId") Long id);

    @Query("SELECT r FROM Reward r JOIN r.event e ORDER BY e.eventDate DESC, r.displayOrder ASC")
    List<Reward> findAllOrderByEventDateDesc();
}

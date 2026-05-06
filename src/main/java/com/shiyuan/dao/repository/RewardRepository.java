package com.shiyuan.dao.repository;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.shiyuan.dao.entity.db.Reward;

public interface RewardRepository extends CrudRepository<Reward, Long>{

	
	
    @Query("SELECT o FROM Reward r WHERE r.event.id = :eventId order by r.displayOrder")
    
    List<Reward> findRewardsByEventId(@Param("eventId") Long id);
	

}

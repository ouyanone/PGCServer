package com.shiyuan.dao.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.entity.db.ScoreStatistic;

public interface PlayerScoreRepository extends CrudRepository<PlayerScore, Long>{
	
	

	@Query("SELECT p FROM PlayerScore p WHERE p.player.id=?1 ORDER BY p.scoreDate DESC LIMIT 3")
	List<PlayerScore> getLast3Records(Long playerId);
	

	//List<ScoreStatistic> getScoreStatistics();
	
}

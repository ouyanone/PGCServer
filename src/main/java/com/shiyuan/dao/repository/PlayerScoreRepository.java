package com.shiyuan.dao.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.entity.db.ScoreStatistic;

public interface PlayerScoreRepository extends CrudRepository<PlayerScore, Long>{
	
	

	@Query("SELECT p FROM PlayerScore p WHERE p.player.id=?1 ORDER BY p.scoreDate DESC LIMIT 3")
	List<PlayerScore> getLast3Records(Long playerId);

	@Modifying
	@Query("DELETE FROM PlayerScore ps WHERE ps.tee.id IN (SELECT t.id FROM Tee t WHERE t.event.id = :eventId)")
	void deleteByEventId(Long eventId);

	@Modifying
	@Query("DELETE FROM PlayerScore ps WHERE ps.id = :id")
	void deletePlayerScoreById(Long id);
	
	
	
	
	@Query("SELECT p FROM PlayerScore p WHERE p.tee.event.id=?1 ORDER BY p.score")
	List<PlayerScore> getPlayerScoreForEvent(Long eventId);
	
	List<PlayerScore>  findByTeeId(Long id);
	
}

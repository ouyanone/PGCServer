package com.shiyuan.dao.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

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

	/**
	 * One player's rounds for the score-history page, newest first. Only events that have been
	 * played out count, and rows without a gross score are roster entries for players who did
	 * not play.
	 */
	@Query("SELECT ps FROM PlayerScore ps "
			+ "WHERE ps.player.id = :playerId AND ps.tee.event.status IN :statuses "
			+ "AND ps.score > 0 "
			+ "ORDER BY ps.tee.event.eventDate DESC, ps.tee.event.id DESC")
	List<PlayerScore> findHistoryForPlayer(@Param("playerId") Long playerId,
			@Param("statuses") List<String> statuses);

	/**
	 * A player's round differentials, most recent first, for events that have been played.
	 * Rows without a gross score are roster entries for players who did not play, and must
	 * not drag the average down. Limit via Pageable.
	 */
	@Query("SELECT ps.currentRoundHandicap FROM PlayerScore ps "
			+ "WHERE ps.player.id = :playerId AND ps.tee.event.status IN :statuses "
			+ "AND ps.score > 0 AND ps.currentRoundHandicap IS NOT NULL "
			+ "ORDER BY ps.tee.event.eventDate DESC, ps.tee.event.id DESC")
	List<Double> findRecentDifferentials(@Param("playerId") Long playerId,
			@Param("statuses") List<String> statuses,
			Pageable pageable);

}

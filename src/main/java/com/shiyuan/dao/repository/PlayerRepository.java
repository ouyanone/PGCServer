package com.shiyuan.dao.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.shiyuan.dao.entity.db.Player;

public interface PlayerRepository extends CrudRepository<Player, Long>{

	
	Player findFirstByGhinNumber(String id);
	
	Iterable<Player> findByIsActive(boolean isActive);

	Player findByNickName(String trim);

	List<Player> findByIsActiveTrueOrderByPgcPointsDesc();

	@Modifying
	@Query("UPDATE Player p SET p.pgcPoints = :points WHERE p.id = :id")
	void updatePgcPoints(@Param("id") Long id, @Param("points") int points);
	

}

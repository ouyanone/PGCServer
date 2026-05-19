package com.shiyuan.dao.repository;

import java.sql.Date;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.shiyuan.dao.entity.db.Season;

public interface SeasonRepository extends CrudRepository<Season, Long>{

	Season findFirstBySeasonName(String seasonName);

	@Query("SELECT s FROM Season s WHERE s.start <= ?1 AND s.end >= ?1")
	Season findCurrentSeason(Date today);
}

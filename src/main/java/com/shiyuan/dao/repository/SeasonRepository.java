package com.shiyuan.dao.repository;

import org.springframework.data.repository.CrudRepository;

import com.shiyuan.dao.entity.db.Season;

public interface SeasonRepository extends CrudRepository<Season, Long>{
	
	Season findFirstBySeasonName(String seasonName);

}

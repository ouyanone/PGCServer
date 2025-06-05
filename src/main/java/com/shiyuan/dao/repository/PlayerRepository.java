package com.shiyuan.dao.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import com.shiyuan.dao.entity.db.Player;

public interface PlayerRepository extends CrudRepository<Player, Long>{

	
	Player findFirstByGhinNumber(String id);
	
	Iterable<Player> findByIsActive(boolean isActive);

	Player findByNickName(String trim);
	

}

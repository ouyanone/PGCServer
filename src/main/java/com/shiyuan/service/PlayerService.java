package com.shiyuan.service;

import java.util.List;

import com.shiyuan.dao.entity.db.Player;

public interface PlayerService {

    List<Player> getAllPlayers();

    void syncHandicap(Long id, String bearerToken) throws Exception;

    Player savePlayer(Player player);

    Player getPlayerById(String id);

    void updateLast3Score();
}

package com.shiyuan.service;

import java.util.List;

import com.shiyuan.dao.entity.db.Player;

public interface PlayerService {

    List<Player> getAllPlayers();

    /**
     * @param includeInactive when false (the default everywhere else) only active players are
     *        returned, which is what the donation and reward pickers expect. The players grid
     *        asks for true so it can offer an "All users" view.
     */
    List<Player> getAllPlayers(boolean includeInactive);

    void syncHandicap(Long id, String bearerToken) throws Exception;

    Player savePlayer(Player player);

    Player getPlayerById(String id);

    void updateLast3Score();
}

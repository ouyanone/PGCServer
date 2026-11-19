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

    /** {@code /api/admin}: a new player from the writable fields; refuses duplicates (409). */
    Player addPlayer(com.shiyuan.controller.api.AdminApiDtos.PlayerFields fields);

    /** {@code /api/admin}: changes only the non-null fields; everything else is left as it is. */
    Player updatePlayer(long id, com.shiyuan.controller.api.AdminApiDtos.PlayerFields fields);

    Player getPlayerById(String id);

    void updateLast3Score();
}

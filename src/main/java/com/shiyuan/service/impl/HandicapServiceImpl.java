package com.shiyuan.service.impl;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.repository.PlayerRepository;
import com.shiyuan.dao.repository.PlayerScoreRepository;
import com.shiyuan.service.HandicapCalculator;
import com.shiyuan.service.HandicapService;

@Service
public class HandicapServiceImpl implements HandicapService {

    /**
     * Statuses that count as "played". CLOSED is simply an archived FINISHED event, so
     * excluding it would make filing an old event silently change everyone's handicap.
     */
    private static final List<String> PLAYED_STATUSES = List.of("FINISHED", "CLOSED");

    @Autowired
    private PlayerScoreRepository playerScoreRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Override
    @Transactional
    public int recalculateForEvent(Long eventId) {
        return recalculate(distinctPlayersIn(eventId));
    }

    @Override
    @Transactional
    public int recalculateAll() {
        return recalculate(playerRepository.findByIsActiveTrueOrderByPgcPointsDesc());
    }

    /** A player can hold only one score row per event, but de-duplicate defensively. */
    private Collection<Player> distinctPlayersIn(Long eventId) {
        Map<Long, Player> byId = new LinkedHashMap<>();
        for (PlayerScore ps : playerScoreRepository.getPlayerScoreForEvent(eventId)) {
            Player player = ps.getPlayer();
            if (player != null && player.getId() != null) {
                byId.putIfAbsent(player.getId(), player);
            }
        }
        return byId.values();
    }

    private int recalculate(Collection<Player> players) {
        int updated = 0;
        for (Player player : players) {
            if (player == null || player.getId() == null) {
                continue;
            }
            List<Double> recent = playerScoreRepository.findRecentDifferentials(
                    player.getId(), PLAYED_STATUSES,
                    PageRequest.of(0, HandicapCalculator.ROUNDS_COUNTED));

            // No rounds played yet: keep whatever handicap they already have rather than
            // overwriting it with a zero or a null.
            if (recent == null || recent.stream().filter(Objects::nonNull).findAny().isEmpty()) {
                continue;
            }
            player.setPgcHandicap(HandicapCalculator.rollingHandicap(
                    recent.stream().filter(Objects::nonNull).toList()));
            playerRepository.save(player);
            updated++;
        }
        return updated;
    }
}

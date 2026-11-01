package com.shiyuan.service;

import com.shiyuan.dto.PlayerScoreHistory;

/** Assembles one player's full round history for the public score-history page (STEP45). */
public interface ScoreHistoryService {

    PlayerScoreHistory getHistory(Long playerId);
}

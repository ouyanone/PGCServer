package com.shiyuan.service;

import java.util.List;

import com.shiyuan.dao.entity.PlayerGolfScore;
import com.shiyuan.dao.entity.PlayerGolfScoreEntrys;
import com.shiyuan.dao.entity.db.PlayerScore;

public interface ScoreService {

    List<PlayerScore> findPlayerScoreForEvent(Long eventId);

    void submitPlayerScore(List<PlayerGolfScore> scores);

    void addEventPlayer(List<PlayerGolfScoreEntrys> scorePlayerEntrys);
}

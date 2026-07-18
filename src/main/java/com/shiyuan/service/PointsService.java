package com.shiyuan.service;

import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dto.EventScoreDetail;
import java.util.List;

public interface PointsService {
    void calculateCurrentSeasonPoints();
    List<Player> getStandings();
    List<EventScoreDetail> getCurrentSeasonGameScores();
    EventScoreDetail getEventScoreDetail(Long eventId);
    List<EventScoreDetail> getTournamentScoreDetails(Long tournamentId);
}

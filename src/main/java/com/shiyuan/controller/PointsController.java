package com.shiyuan.controller;

import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dto.EventScoreDetail;
import com.shiyuan.service.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class PointsController {

    @Autowired
    private PointsService pointsService;

    @PostMapping("/webapi/admin/points/calculate")
    public ResponseEntity<Map<String, String>> calculatePoints() {
        pointsService.calculateCurrentSeasonPoints();
        return ResponseEntity.ok(Map.of("message", "Points calculated successfully"));
    }

    @GetMapping("/webapi/points/standings")
    public ResponseEntity<List<Player>> getStandings() {
        return ResponseEntity.ok(pointsService.getStandings());
    }

    @GetMapping("/webapi/points/game-scores")
    public ResponseEntity<List<EventScoreDetail>> getGameScores() {
        return ResponseEntity.ok(pointsService.getCurrentSeasonGameScores());
    }
}

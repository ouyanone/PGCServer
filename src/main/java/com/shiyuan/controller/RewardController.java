package com.shiyuan.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shiyuan.dao.entity.db.Reward;
import com.shiyuan.service.RewardService;

import io.micrometer.common.util.StringUtils;

/**
 * REST API for managing event rewards.
 *
 * GET    /webapi/rewards?eventId={id}  — list rewards for an event (sorted by displayOrder)
 * GET    /webapi/rewards/{id}          — get reward by id
 * POST   /webapi/rewards               — create a new reward
 * PUT    /webapi/rewards/{id}          — update an existing reward
 * DELETE /webapi/rewards/{id}          — delete a reward
 *
 * Request body for create/update:
 * {
 *   "rewardName": "最近洞奖",
 *   "rewardDesc": "Closest to pin",
 *   "rewardStory": "...",
 *   "displayOrder": 1,
 *   "event": { "id": 1144 },
 *   "player": { "id": 1000 }
 * }
 */
@RestController
public class RewardController extends BaseController {

    @Autowired
    RewardService rewardService;

    @GetMapping(path = "/webapi/rewards/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Reward>> getAllRewards() {
        return ResponseEntity.ok(rewardService.getAllRewards());
    }

    @GetMapping(path = "/webapi/rewards", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Reward>> getRewardsForEvent(@RequestParam(required = false) String eventId) {
        if (StringUtils.isEmpty(eventId)) {
            eventId = "1144";
        }
        List<Reward> rewardList = rewardService.findRewardForEvent(Long.parseLong(eventId));
        return ResponseEntity.ok(rewardList);
    }

    @GetMapping(path = "/webapi/rewards/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Reward> getRewardById(@PathVariable Long id) {
        Reward reward = rewardService.getRewardById(id);
        if (reward == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(reward);
    }

    @PostMapping(path = "/webapi/rewards", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Reward> createReward(@RequestBody Reward reward) {
        Reward created = rewardService.createReward(reward);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping(path = "/webapi/rewards/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Reward> updateReward(@PathVariable Long id, @RequestBody Reward reward) {
        Reward updated = rewardService.updateReward(id, reward);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping(path = "/webapi/rewards/{id}")
    public ResponseEntity<Void> deleteReward(@PathVariable Long id) {
        boolean deleted = rewardService.deleteReward(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}

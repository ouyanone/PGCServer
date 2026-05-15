package com.shiyuan.service;

import java.util.List;

import com.shiyuan.dao.entity.db.Reward;

public interface RewardService {

    /** Returns all rewards across all events, sorted by event date desc then displayOrder. */
    List<Reward> getAllRewards();

    /** Returns all rewards for a given event, sorted by displayOrder. */
    List<Reward> findRewardForEvent(Long eventId);

    /** Returns a single reward by id, or null if not found. */
    Reward getRewardById(Long id);

    /** Creates and persists a new reward. */
    Reward createReward(Reward reward);

    /** Updates an existing reward. Returns null if id not found. */
    Reward updateReward(Long id, Reward reward);

    /** Deletes a reward by id. Returns false if not found. */
    boolean deleteReward(Long id);
}

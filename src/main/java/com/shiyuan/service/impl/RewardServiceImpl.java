package com.shiyuan.service.impl;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shiyuan.dao.entity.db.Reward;
import com.shiyuan.dao.repository.RewardRepository;
import com.shiyuan.service.RewardService;

@Service
public class RewardServiceImpl implements RewardService {

    @Autowired
    RewardRepository rewardRepository;

    @Override
    public List<Reward> getAllRewards() {
        return rewardRepository.findAllOrderByEventDateDesc();
    }

    @Override
    public List<Reward> findRewardForEvent(Long eventId) {
        List<Reward> rewards = rewardRepository.findRewardsByEventId(eventId);
        Collections.sort(rewards, Comparator.comparing(Reward::getDisplayOrder));
        return rewards;
    }

    @Override
    public Reward getRewardById(Long id) {
        return rewardRepository.findById(id).orElse(null);
    }

    @Override
    public Reward createReward(Reward reward) {
        reward.setId(null); // ensure insert, not update
        return rewardRepository.save(reward);
    }

    @Override
    public Reward updateReward(Long id, Reward reward) {
        Optional<Reward> existing = rewardRepository.findById(id);
        if (existing.isEmpty()) {
            return null;
        }
        reward.setId(id);
        return rewardRepository.save(reward);
    }

    @Override
    public boolean deleteReward(Long id) {
        if (!rewardRepository.existsById(id)) {
            return false;
        }
        rewardRepository.deleteById(id);
        return true;
    }
}

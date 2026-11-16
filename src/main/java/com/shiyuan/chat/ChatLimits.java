package com.shiyuan.chat;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.repository.ChatDailyCountRepository;

/** At most 30 questions per member per New York calendar day. */
@Service
public class ChatLimits {

    static final int PER_DAY = 30;

    private final ChatDailyCountRepository repo;
    private final Clock clock;

    public ChatLimits(ChatDailyCountRepository repo, Clock chatClock) {
        this.repo = repo;
        this.clock = chatClock;
    }

    /** Counts this question; false when it is over today's limit. */
    @Transactional
    public boolean tryConsume(String memberSub) {
        LocalDate today = LocalDate.now(clock);
        repo.increment(memberSub, today);
        return repo.questionsOn(memberSub, today) <= PER_DAY;
    }

    /** Gives a question back when it failed for reasons that are not the member's doing. */
    @Transactional
    public void refund(String memberSub) {
        repo.decrement(memberSub, LocalDate.now(clock));
    }
}

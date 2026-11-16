package com.shiyuan.chat;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.db.ChatExchange;
import com.shiyuan.dao.repository.ChatExchangeRepository;

/** Each member's last three exchanges. Saving a fourth deletes the oldest. */
@Service
public class ChatHistoryService {

    private final ChatExchangeRepository repo;
    private final Clock clock;

    public ChatHistoryService(ChatExchangeRepository repo, Clock chatClock) {
        this.repo = repo;
        this.clock = chatClock;
    }

    /** Oldest first, at most three, only this member's. */
    @Transactional(readOnly = true)
    public List<ChatExchangeDto> recent(String memberSub) {
        List<ChatExchangeDto> out = new ArrayList<>();
        for (ChatExchange e : repo.findTop3ByMemberSubOrderByCreatedAtDescIdDesc(memberSub)) {
            out.add(new ChatExchangeDto(e.getQuestion(), e.getAnswer(), e.getCreatedAt()));
        }
        Collections.reverse(out);
        return out;
    }

    @Transactional
    public void save(String memberSub, String question, String answer) {
        ChatExchange e = new ChatExchange();
        e.setMemberSub(memberSub);
        e.setQuestion(question);
        e.setAnswer(answer);
        e.setCreatedAt(clock.instant());
        repo.save(e);

        List<Long> keep = repo.findTop3ByMemberSubOrderByCreatedAtDescIdDesc(memberSub)
                .stream().map(ChatExchange::getId).toList();
        if (!keep.isEmpty()) {
            repo.deleteByMemberSubAndIdNotIn(memberSub, keep);
        }
    }
}

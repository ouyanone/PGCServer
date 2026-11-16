package com.shiyuan.dao.entity.db;

import jakarta.persistence.*;
import java.time.Instant;

/** One question and its answer, kept so the member sees (and the model gets) the last three. */
@Entity
@Table(name = "chat_exchange")
public class ChatExchange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_sub", nullable = false)
    private String memberSub;

    @Column(name = "question", nullable = false)
    private String question;

    @Column(name = "answer", nullable = false)
    private String answer;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMemberSub() { return memberSub; }
    public void setMemberSub(String memberSub) { this.memberSub = memberSub; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

package com.shiyuan.dao.entity.db;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Questions one member asked on one New York calendar day. Written only by native upserts. */
@Entity
@Table(name = "chat_daily_count")
@IdClass(ChatDailyCount.Key.class)
public class ChatDailyCount {

    @Id
    @Column(name = "member_sub")
    private String memberSub;

    @Id
    @Column(name = "day")
    private LocalDate day;

    @Column(name = "questions", nullable = false)
    private int questions;

    public String getMemberSub() { return memberSub; }
    public LocalDate getDay() { return day; }
    public int getQuestions() { return questions; }

    public static class Key implements Serializable {
        private String memberSub;
        private LocalDate day;

        public Key() {}

        public Key(String memberSub, LocalDate day) {
            this.memberSub = memberSub;
            this.day = day;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(memberSub, k.memberSub) && Objects.equals(day, k.day);
        }

        @Override
        public int hashCode() {
            return Objects.hash(memberSub, day);
        }
    }
}

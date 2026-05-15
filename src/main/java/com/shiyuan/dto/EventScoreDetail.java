package com.shiyuan.dto;

import java.time.LocalDate;
import java.util.List;

public class EventScoreDetail {

    private Long eventId;
    private String eventName;
    private LocalDate eventDate;
    private String courseName;
    private List<PlayerScoreRow> scores;
    private List<RewardRow> rewards;

    public EventScoreDetail(Long eventId, String eventName, LocalDate eventDate,
                            String courseName, List<PlayerScoreRow> scores, List<RewardRow> rewards) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.courseName = courseName;
        this.scores = scores;
        this.rewards = rewards;
    }

    public Long getEventId() { return eventId; }
    public String getEventName() { return eventName; }
    public LocalDate getEventDate() { return eventDate; }
    public String getCourseName() { return courseName; }
    public List<PlayerScoreRow> getScores() { return scores; }
    public List<RewardRow> getRewards() { return rewards; }

    public static class RewardRow {
        private String rewardName;
        private String rewardDesc;
        private String rewardStory;
        private String playerName;
        private Integer displayOrder;
        private String rewardGroup;

        public RewardRow(String rewardName, String rewardDesc, String rewardStory,
                         String playerName, Integer displayOrder, String rewardGroup) {
            this.rewardName = rewardName;
            this.rewardDesc = rewardDesc;
            this.rewardStory = rewardStory;
            this.playerName = playerName;
            this.displayOrder = displayOrder;
            this.rewardGroup = rewardGroup;
        }

        public String getRewardName() { return rewardName; }
        public String getRewardDesc() { return rewardDesc; }
        public String getRewardStory() { return rewardStory; }
        public String getPlayerName() { return playerName; }
        public Integer getDisplayOrder() { return displayOrder; }
        public String getRewardGroup() { return rewardGroup; }
    }

    public static class PlayerScoreRow {
        private String playerName;
        private Integer hole1, hole2, hole3, hole4, hole5, hole6, hole7, hole8, hole9;
        private Integer hole10, hole11, hole12, hole13, hole14, hole15, hole16, hole17, hole18;
        private Integer front9;
        private Integer back9;
        private Integer totalScore;
        private Double netScore;
        private Integer gamePoint;

        public PlayerScoreRow(String playerName,
                              Integer h1, Integer h2, Integer h3, Integer h4, Integer h5,
                              Integer h6, Integer h7, Integer h8, Integer h9,
                              Integer h10, Integer h11, Integer h12, Integer h13, Integer h14,
                              Integer h15, Integer h16, Integer h17, Integer h18,
                              Integer totalScore, Double netScore, Integer gamePoint) {
            this.playerName = playerName;
            this.hole1 = h1; this.hole2 = h2; this.hole3 = h3;
            this.hole4 = h4; this.hole5 = h5; this.hole6 = h6;
            this.hole7 = h7; this.hole8 = h8; this.hole9 = h9;
            this.hole10 = h10; this.hole11 = h11; this.hole12 = h12;
            this.hole13 = h13; this.hole14 = h14; this.hole15 = h15;
            this.hole16 = h16; this.hole17 = h17; this.hole18 = h18;
            this.totalScore = totalScore;
            this.netScore = netScore;
            this.gamePoint = gamePoint;
            this.front9 = sum(h1, h2, h3, h4, h5, h6, h7, h8, h9);
            this.back9  = sum(h10, h11, h12, h13, h14, h15, h16, h17, h18);
        }

        private static int sum(Integer... vals) {
            int total = 0;
            for (Integer v : vals) { if (v != null) total += v; }
            return total;
        }

        public String getPlayerName() { return playerName; }
        public Integer getHole1() { return hole1; }
        public Integer getHole2() { return hole2; }
        public Integer getHole3() { return hole3; }
        public Integer getHole4() { return hole4; }
        public Integer getHole5() { return hole5; }
        public Integer getHole6() { return hole6; }
        public Integer getHole7() { return hole7; }
        public Integer getHole8() { return hole8; }
        public Integer getHole9() { return hole9; }
        public Integer getHole10() { return hole10; }
        public Integer getHole11() { return hole11; }
        public Integer getHole12() { return hole12; }
        public Integer getHole13() { return hole13; }
        public Integer getHole14() { return hole14; }
        public Integer getHole15() { return hole15; }
        public Integer getHole16() { return hole16; }
        public Integer getHole17() { return hole17; }
        public Integer getHole18() { return hole18; }
        public Integer getFront9() { return front9; }
        public Integer getBack9() { return back9; }
        public Integer getTotalScore() { return totalScore; }
        public Double getNetScore() { return netScore; }
        public Integer getGamePoint() { return gamePoint; }
    }
}

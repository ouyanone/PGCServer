package com.shiyuan.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * One player's complete round history (STEP45).
 *
 * Hole values here are real strokes, not the offsets-from-par that player_score stores — the
 * service converts them using the course's own per-hole par. When a round's course or its
 * per-hole par is missing, {@code pars} and {@code coursePar} are null and {@code holes} falls
 * back to the stored offsets, so one badly configured course cannot blank out a whole history.
 */
public class PlayerScoreHistory {

    private Long playerId;
    private String firstName;
    private String lastName;
    private Double pgcHandicap;
    private Double handicap;

    private int roundsPlayed;
    private Integer bestGross;
    private Double averageGross;
    private Integer latestGross;

    /** Which season is current today, so the view can default to it. Null off-season. */
    private Long currentSeasonId;
    private String currentSeasonName;

    private List<Round> rounds;

    public PlayerScoreHistory() {
    }

    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long v) { playerId = v; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { firstName = v; }

    public String getLastName() { return lastName; }
    public void setLastName(String v) { lastName = v; }

    public Double getPgcHandicap() { return pgcHandicap; }
    public void setPgcHandicap(Double v) { pgcHandicap = v; }

    public Double getHandicap() { return handicap; }
    public void setHandicap(Double v) { handicap = v; }

    public int getRoundsPlayed() { return roundsPlayed; }
    public void setRoundsPlayed(int v) { roundsPlayed = v; }

    public Integer getBestGross() { return bestGross; }
    public void setBestGross(Integer v) { bestGross = v; }

    public Double getAverageGross() { return averageGross; }
    public void setAverageGross(Double v) { averageGross = v; }

    public Integer getLatestGross() { return latestGross; }
    public void setLatestGross(Integer v) { latestGross = v; }

    public Long getCurrentSeasonId() { return currentSeasonId; }
    public void setCurrentSeasonId(Long v) { currentSeasonId = v; }

    public String getCurrentSeasonName() { return currentSeasonName; }
    public void setCurrentSeasonName(String v) { currentSeasonName = v; }

    public List<Round> getRounds() { return rounds; }
    public void setRounds(List<Round> v) { rounds = v; }

    /** A single round, newest first in {@link PlayerScoreHistory#getRounds()}. */
    public static class Round {

        private Long eventId;
        private String eventName;
        private LocalDate eventDate;
        private String clubName;
        private String courseName;
        private Integer coursePar;
        private Long seasonId;
        private String seasonName;

        private Integer gross;
        private Double netScore;
        private Double differential;
        private Integer gamePoint;

        private Integer[] holes;
        private Integer[] pars;

        private int outStrokes;
        private int inStrokes;
        private int outPar;
        private int inPar;

        private int eagles;
        private int birdies;
        private int parsMade;

        public Long getEventId() { return eventId; }
        public void setEventId(Long v) { eventId = v; }

        public String getEventName() { return eventName; }
        public void setEventName(String v) { eventName = v; }

        public LocalDate getEventDate() { return eventDate; }
        public void setEventDate(LocalDate v) { eventDate = v; }

        public String getClubName() { return clubName; }
        public void setClubName(String v) { clubName = v; }

        public String getCourseName() { return courseName; }
        public void setCourseName(String v) { courseName = v; }

        public Integer getCoursePar() { return coursePar; }
        public void setCoursePar(Integer v) { coursePar = v; }

        public Long getSeasonId() { return seasonId; }
        public void setSeasonId(Long v) { seasonId = v; }

        public String getSeasonName() { return seasonName; }
        public void setSeasonName(String v) { seasonName = v; }

        public Integer getGross() { return gross; }
        public void setGross(Integer v) { gross = v; }

        public Double getNetScore() { return netScore; }
        public void setNetScore(Double v) { netScore = v; }

        public Double getDifferential() { return differential; }
        public void setDifferential(Double v) { differential = v; }

        public Integer getGamePoint() { return gamePoint; }
        public void setGamePoint(Integer v) { gamePoint = v; }

        public Integer[] getHoles() { return holes; }
        public void setHoles(Integer[] v) { holes = v; }

        public Integer[] getPars() { return pars; }
        public void setPars(Integer[] v) { pars = v; }

        public int getOutStrokes() { return outStrokes; }
        public void setOutStrokes(int v) { outStrokes = v; }

        public int getInStrokes() { return inStrokes; }
        public void setInStrokes(int v) { inStrokes = v; }

        public int getOutPar() { return outPar; }
        public void setOutPar(int v) { outPar = v; }

        public int getInPar() { return inPar; }
        public void setInPar(int v) { inPar = v; }

        public int getEagles() { return eagles; }
        public void setEagles(int v) { eagles = v; }

        public int getBirdies() { return birdies; }
        public void setBirdies(int v) { birdies = v; }

        public int getParsMade() { return parsMade; }
        public void setParsMade(int v) { parsMade = v; }
    }
}

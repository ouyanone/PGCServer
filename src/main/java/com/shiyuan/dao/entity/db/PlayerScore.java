package com.shiyuan.dao.entity.db;

import java.sql.Date;
import java.time.LocalDate;

import org.hibernate.annotations.Cascade;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;


@Entity
@Table(name = "player_score")
public class PlayerScore {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "record_story")
	private String recordStory;
	
	@Column(name = "entry_score")
	private Double entryScore;

	@Column(name = "score")
	private Integer score;
	
	@Column(name = "flight")
	private Integer flight;
	
	@Column(name = "score_date")
	private LocalDate scoreDate;
	
	@Column(name = "hole_1")
	private Integer hole1;
	
	@Column(name = "hole_2")
	private Integer hole2;
	
	@Column(name = "hole_3")
	private Integer hole3;
	
	@Column(name = "hole_4")
	private Integer hole4;
	
	@Column(name = "hole_5")
	private Integer hole5;
	
	@Column(name = "hole_6")
	private Integer hole6;
	
	@Column(name = "hole_7")
	private Integer hole7;
	
	@Column(name = "hole_8")
	private Integer hole8;
	
	@Column(name = "hole_9")
	private Integer hole9;
	
	@Column(name = "hole_10")
	private Integer hole10;
	
	@Column(name = "hole_11")
	private Integer hole11;
	
	@Column(name = "hole_12")
	private Integer hole12;
	
	@Column(name = "hole_13")
	private Integer hole13;
	
	@Column(name = "hole_14")
	private Integer hole14;
	
	@Column(name = "hole_15")
	private Integer hole15;
	
	@Column(name = "hole_16")
	private Integer hole16;
	
	@Column(name = "hole_17")
	private Integer hole17;
	
	@Column(name = "hole_18")
	private Integer hole18;
	
	
	@Column(name = "game_point")
	private Integer gamePoint;

	@Column(name = "net_score")
	private Double netScore;
	
	@Column(name = "entry_pgc_handicap")
	private Double entryPGCHandicap;
	
	@Column(name = "current_handicap")
	private Double currentRoundHandicap;
	
	
	/*
	 * @Column(name = "team_win") private Integer teamWin;
	 * 
	 * @Column(name = "tee_win") private Integer teeWin;
	 */
	
	@ManyToOne
	@JoinColumn(name="player_id")
	private Player player;
	
	@JsonIgnore
	@ManyToOne
	@JoinColumn(name="tee_id")
	private Tee tee;
	/*
	 * @JsonIgnore
	 * 
	 * @ManyToOne
	 * 
	 * @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
	 * 
	 * @JoinColumn(name="player_id") private Player teeTeamXref;
	 */

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getRecordStory() {
		return recordStory;
	}

	public void setRecordStory(String recordStory) {
		this.recordStory = recordStory;
	}

	public Double getEntryScore() {
		return entryScore;
	}

	public void setEntryScore(Double entryScore) {
		this.entryScore = entryScore;
	}

	public Integer getScore() {
		return score;
	}

	public void setScore(Integer score) {
		this.score = score;
	}

	public LocalDate getScoreDate() {
		return scoreDate;
	}

	public void setScoreDate(LocalDate scoreDate) {
		this.scoreDate = scoreDate;
	}

	/*
	 * public Integer getTeamWin() { return teamWin; }
	 * 
	 * public void setTeamWin(Integer teamWin) { this.teamWin = teamWin; }
	 * 
	 * public Integer getTeeWin() { return teeWin; }
	 * 
	 * public void setTeeWin(Integer teeWin) { this.teeWin = teeWin; }
	 */

	public Player getPlayer() {
		return player;
	}

	public void setPlayer(Player player) {
		this.player = player;
	}


	public Tee getTee() {
		return tee;
	}

	public void setTee(Tee tee) {
		this.tee = tee;
	}

	public Integer getGamePoint() {
		return gamePoint;
	}

	public void setGamePoint(Integer gamePoint) {
		this.gamePoint = gamePoint;
	}

	public Double getNetScore() {
		return netScore;
	}

	public void setNetScore(Double netScore) {
		this.netScore = netScore;
	}

	public Integer getFlight() {
		return flight;
	}

	public void setFlight(Integer flight) {
		this.flight = flight;
	}

	public Integer getHole1() {
		return hole1;
	}

	public void setHole1(Integer hole1) {
		this.hole1 = hole1;
	}

	public Integer getHole2() {
		return hole2;
	}

	public void setHole2(Integer hole2) {
		this.hole2 = hole2;
	}

	public Integer getHole3() {
		return hole3;
	}

	public void setHole3(Integer hole3) {
		this.hole3 = hole3;
	}

	public Integer getHole4() {
		return hole4;
	}

	public void setHole4(Integer hole4) {
		this.hole4 = hole4;
	}

	public Integer getHole5() {
		return hole5;
	}

	public void setHole5(Integer hole5) {
		this.hole5 = hole5;
	}

	public Integer getHole6() {
		return hole6;
	}

	public void setHole6(Integer hole6) {
		this.hole6 = hole6;
	}

	public Integer getHole7() {
		return hole7;
	}

	public void setHole7(Integer hole7) {
		this.hole7 = hole7;
	}

	public Integer getHole8() {
		return hole8;
	}

	public void setHole8(Integer hole8) {
		this.hole8 = hole8;
	}

	public Integer getHole9() {
		return hole9;
	}

	public void setHole9(Integer hole9) {
		this.hole9 = hole9;
	}

	public Integer getHole10() {
		return hole10;
	}

	public void setHole10(Integer hole10) {
		this.hole10 = hole10;
	}

	public Integer getHole11() {
		return hole11;
	}

	public void setHole11(Integer hole11) {
		this.hole11 = hole11;
	}

	public Integer getHole12() {
		return hole12;
	}

	public void setHole12(Integer hole12) {
		this.hole12 = hole12;
	}

	public Integer getHole13() {
		return hole13;
	}

	public void setHole13(Integer hole13) {
		this.hole13 = hole13;
	}

	public Integer getHole14() {
		return hole14;
	}

	public void setHole14(Integer hole14) {
		this.hole14 = hole14;
	}

	public Integer getHole15() {
		return hole15;
	}

	public void setHole15(Integer hole15) {
		this.hole15 = hole15;
	}

	public Integer getHole16() {
		return hole16;
	}

	public void setHole16(Integer hole16) {
		this.hole16 = hole16;
	}

	public Integer getHole17() {
		return hole17;
	}

	public void setHole17(Integer hole17) {
		this.hole17 = hole17;
	}

	public Integer getHole18() {
		return hole18;
	}

	public void setHole18(Integer hole18) {
		this.hole18 = hole18;
	}

	public Double getEntryPGCHandicap() {
		return entryPGCHandicap;
	}

	public void setEntryPGCHandicap(Double entryPGCHandicap) {
		this.entryPGCHandicap = entryPGCHandicap;
	}

	public Double getCurrentRoundHandicap() {
		return currentRoundHandicap;
	}

	public void setCurrentRoundHandicap(Double currentRoundHandicap) {
		this.currentRoundHandicap = currentRoundHandicap;
	}

	@Override
	public String toString() {
		return "PlayerScore [id=" + id + ", recordStory=" + recordStory + ", entryScore=" + entryScore + ", score="
				+ score + ", scoreDate=" + scoreDate + ", teamWin="  + ", player="
				+ player + ", teeTeamXref=" +  "]";
	}
}

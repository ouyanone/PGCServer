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
	
	@Column(name = "score_date")
	private LocalDate scoreDate;
	
	@Column(name = "team_win")
	private Integer teamWin;
	
	@Column(name = "tee_win")
	private Integer teeWin;
	
	@ManyToOne
	@JoinColumn(name="player_id")
	private Player player;
	
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

	public Integer getTeamWin() {
		return teamWin;
	}

	public void setTeamWin(Integer teamWin) {
		this.teamWin = teamWin;
	}

	public Integer getTeeWin() {
		return teeWin;
	}

	public void setTeeWin(Integer teeWin) {
		this.teeWin = teeWin;
	}

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

	@Override
	public String toString() {
		return "PlayerScore [id=" + id + ", recordStory=" + recordStory + ", entryScore=" + entryScore + ", score="
				+ score + ", scoreDate=" + scoreDate + ", teamWin=" + teamWin + ", teeWin=" + teeWin + ", player="
				+ player + ", teeTeamXref=" +  "]";
	}
}

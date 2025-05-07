package com.shiyuan.dao.entity.db;

import java.util.List;

import org.hibernate.annotations.Cascade;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "tee_team_xref")
public class TeeTeamXref {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tee_team_story")
	private String teeTeamStory;
	
	@Column(name = "score")
	private Integer score;
	
	@JsonIgnore
	@ManyToOne
	@Cascade(value={org.hibernate.annotations.CascadeType.ALL})
	@JoinColumn(name="tee_id")
	private Tee tee;
	
	
	@ManyToOne
	@JoinColumn(name="team_id")
	private Team team;
	
	
	@OneToMany(mappedBy="teeTeamXref" , cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.EAGER)
	private List<PlayerScore> playScoreList;


	public Long getId() {
		return id;
	}


	public void setId(Long id) {
		this.id = id;
	}


	public String getTeeTeamStory() {
		return teeTeamStory;
	}


	public void setTeeTeamStory(String teeTeamStory) {
		this.teeTeamStory = teeTeamStory;
	}


	public Integer getScore() {
		return score;
	}


	public void setScore(Integer score) {
		this.score = score;
	}


	public Tee getTee() {
		return tee;
	}


	public void setTee(Tee tee) {
		this.tee = tee;
	}


	public Team getTeam() {
		return team;
	}


	public void setTeam(Team team) {
		this.team = team;
	}


	public List<PlayerScore> getPlayScoreList() {
		return playScoreList;
	}


	public void setPlayScoreList(List<PlayerScore> playScoreList) {
		this.playScoreList = playScoreList;
	}




}

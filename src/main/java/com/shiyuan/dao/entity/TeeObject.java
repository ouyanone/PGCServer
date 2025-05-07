package com.shiyuan.dao.entity;

import java.util.List;

import com.shiyuan.dao.entity.db.Player;

public class TeeObject {
	
	List<Player> teamA;
	double teamAavgScore;
	double teamAavghandicap;
	List<Player> teamB;
	double teamBavgScore;
	double teamBavghandicap;
	String teeName;
	String teeTime;
	
	public List<Player> getTeamA() {
		return teamA;
	}
	public void setTeamA(List<Player> teamA) {
		this.teamA = teamA;
	}
	public double getTeamAavgScore() {
		return teamAavgScore;
	}
	public void setTeamAavgScore(double teamAavgScore) {
		this.teamAavgScore = teamAavgScore;
	}
	public double getTeamAavghandicap() {
		return teamAavghandicap;
	}
	public void setTeamAavghandicap(double teamAavghandicap) {
		this.teamAavghandicap = teamAavghandicap;
	}
	public List<Player> getTeamB() {
		return teamB;
	}
	public void setTeamB(List<Player> teamB) {
		this.teamB = teamB;
	}
	public double getTeamBavgScore() {
		return teamBavgScore;
	}
	public void setTeamBavgScore(double teamBavgScore) {
		this.teamBavgScore = teamBavgScore;
	}
	public double getTeamBavghandicap() {
		return teamBavghandicap;
	}
	public void setTeamBavghandicap(double teamBavghandicap) {
		this.teamBavghandicap = teamBavghandicap;
	}
	public String getTeeName() {
		return teeName;
	}
	public void setTeeName(String teeName) {
		this.teeName = teeName;
	}
	public String getTeeTime() {
		return teeTime;
	}
	public void setTeeTime(String teeTime) {
		this.teeTime = teeTime;
	}
	@Override
	public String toString() {
		return "Tee [teamA=" + teamA + ", teamAavgScore=" + teamAavgScore + ", teamAavghandicap=" + teamAavghandicap
				+ ", teamB=" + teamB + ", teamBavgScore=" + teamBavgScore + ", teamBavghandicap=" + teamBavghandicap
				+ ", teeName=" + teeName + ", teeTime=" + teeTime + "]";
	}
	
	

}

package com.shiyuan.dao.entity.db;

public class ScoreStatistic {

	private int id;
	
	private String firstName;
	private String lastName;
	
	private int numberOfGames;
	private double avgScore;
	
	private int netTeeWin;
	private int netTeamWin;
	
	public ScoreStatistic(int id, int numberOfGames, double avgScore, int netTeeWin,
			int netTeamWin) {
		this.id = id;
//		this.firstName = firstName;
//		this.lastName = lastName;
		this.numberOfGames = numberOfGames;
		this.avgScore = avgScore;
		this.netTeeWin = netTeeWin;
		this.netTeamWin = netTeamWin;
	}
	
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}

	public int getNumberOfGames() {
		return numberOfGames;
	}
	public void setNumberOfGames(int numberOfGames) {
		this.numberOfGames = numberOfGames;
	}
	public double getAvgScore() {
		return avgScore;
	}
	public void setAvgScore(double avgScore) {
		this.avgScore = avgScore;
	}
	public int getNetTeeWin() {
		return netTeeWin;
	}
	public void setNetTeeWin(int netTeeWin) {
		this.netTeeWin = netTeeWin;
	}
	public int getNetTeamWin() {
		return netTeamWin;
	}
	public void setNetTeamWin(int netTeamWin) {
		this.netTeamWin = netTeamWin;
	}


	public String getFirstName() {
		return firstName;
	}


	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}


	public String getLastName() {
		return lastName;
	}


	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

}

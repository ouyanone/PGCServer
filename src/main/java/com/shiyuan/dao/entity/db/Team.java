package com.shiyuan.dao.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "team")
public class Team {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "team_name")
	private String teamName;
	
	@Column(name = "team_desc")
	private String teamDesc;
	
	@Column(name = "team_chinese_name")
	private String teamChineseName;
	
	@Column(name = "team_color_name")
	private String teamColorName;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTeamName() {
		return teamName;
	}

	public void setTeamName(String teamName) {
		this.teamName = teamName;
	}

	public String getTeamDesc() {
		return teamDesc;
	}

	public void setTeamDesc(String teamDesc) {
		this.teamDesc = teamDesc;
	}

	public String getTeamChineseName() {
		return teamChineseName;
	}

	public void setTeamChineseName(String teamChineseName) {
		this.teamChineseName = teamChineseName;
	}

	public String getTeamColorName() {
		return teamColorName;
	}

	public void setTeamColorName(String teamColorName) {
		this.teamColorName = teamColorName;
	}

	@Override
	public String toString() {
		return "Team [id=" + id + ", teamName=" + teamName + ", teamDesc=" + teamDesc + ", teamChineseName="
				+ teamChineseName + ", teamColorName=" + teamColorName + "]";
	}

}

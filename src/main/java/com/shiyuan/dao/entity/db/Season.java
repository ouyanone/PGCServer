package com.shiyuan.dao.entity.db;

import java.sql.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "season")
public class Season {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "season_name")
	private String seasonName;
	
	@Column(name = "season_desc")
	private String seasonDesc;
	
	@Column(name = "start")
	private Date start;
	
	@Column(name = "end")
	private Date end;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getSeasonName() {
		return seasonName;
	}

	public void setSeasonName(String seasonName) {
		this.seasonName = seasonName;
	}

	public String getSeasonDesc() {
		return seasonDesc;
	}

	public void setSeasonDesc(String seasonDesc) {
		this.seasonDesc = seasonDesc;
	}

	public Date getStart() {
		return start;
	}

	public void setStart(Date start) {
		this.start = start;
	}

	public Date getEnd() {
		return end;
	}

	public void setEnd(Date end) {
		this.end = end;
	}

	@Override
	public String toString() {
		return "Season [id=" + id + ", seasonName=" + seasonName + ", seasonDesc=" + seasonDesc + ", start=" + start
				+ ", end=" + end + "]";
	}

}

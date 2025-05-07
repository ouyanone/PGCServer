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
@Table(name = "tee")
public class Tee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tee_name")
	private String teeName;
	
	@Column(name = "tee_desc")
	private String teeDesc;
	
	@Column(name = "tee_story")
	private String teeStroy;
	
	@Column(name = "tee_time")
	private String teeTime;
	
	@ManyToOne
	@JoinColumn(name="course")
	private Course course;
	
	@JsonIgnore
	@ManyToOne
	@Cascade(value={org.hibernate.annotations.CascadeType.ALL})
	@JoinColumn(name="event_id")
	private Event event;
	
	
	@OneToMany(mappedBy="tee", cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.EAGER)
	private List<TeeTeamXref> teeTeamXrefList;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTeeName() {
		return teeName;
	}

	public void setTeeName(String teeName) {
		this.teeName = teeName;
	}

	public String getTeeDesc() {
		return teeDesc;
	}

	public void setTeeDesc(String teeDesc) {
		this.teeDesc = teeDesc;
	}

	public String getTeeStroy() {
		return teeStroy;
	}

	public void setTeeStroy(String teeStroy) {
		this.teeStroy = teeStroy;
	}

	public Course getCourse() {
		return course;
	}

	public void setCourse(Course course) {
		this.course = course;
	}

	public Event getEvent() {
		return event;
	}

	public void setEvent(Event event) {
		this.event = event;
	}

	public List<TeeTeamXref> getTeeTeamXrefList() {
		return teeTeamXrefList;
	}

	public void setTeeTeamXrefList(List<TeeTeamXref> teeTeamXrefList) {
		this.teeTeamXrefList = teeTeamXrefList;
	}

	public String getTeeTime() {
		return teeTime;
	}

	public void setTeeTime(String teeTime) {
		this.teeTime = teeTime;
	}

	@Override
	public String toString() {
		return "Tee [id=" + id + ", teeName=" + teeName + ", teeDesc=" + teeDesc + ", teeStroy=" + teeStroy
				+ ", course=" + course  +"]";
	}
	
}

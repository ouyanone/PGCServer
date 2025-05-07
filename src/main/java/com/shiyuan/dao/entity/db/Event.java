package com.shiyuan.dao.entity.db;

import java.time.LocalDate;
import java.util.List;

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
@Table(name = "Event")
public class Event {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_name")
	private String eventName;
	
	@Column(name = "event_desc")
	private String eventDesc;	
	
	@Column(name = "event_date")
	private LocalDate eventDate;
	
	@Column(name = "event_story")
	private String eventStory;
	
	@Column(name = "status")
	private String status;
	
	@ManyToOne
	@JoinColumn(name="course")
	private Course course;
	
	@ManyToOne
	@JoinColumn(name="season", nullable=false)
	private Season season;
	
	@ManyToOne
	@JoinColumn(name="host_player")
	private Player player;
	
	@OneToMany(mappedBy="event", cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.EAGER)
	private List<Tee> teeList;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getEventName() {
		return eventName;
	}

	public void setEventName(String eventName) {
		this.eventName = eventName;
	}

	public String getEventDesc() {
		return eventDesc;
	}

	public void setEventDesc(String eventDesc) {
		this.eventDesc = eventDesc;
	}



	public String getEventStory() {
		return eventStory;
	}

	public void setEventStory(String eventStory) {
		this.eventStory = eventStory;
	}

	public Course getCourse() {
		return course;
	}

	public void setCourse(Course course) {
		this.course = course;
	}

	public Season getSeason() {
		return season;
	}

	public void setSeason(Season season) {
		this.season = season;
	}

	public Player getPlayer() {
		return player;
	}

	public void setPlayer(Player player) {
		this.player = player;
	}

	public List<Tee> getTeeList() {
		return teeList;
	}

	public void setTeeList(List<Tee> teeList) {
		this.teeList = teeList;
	}

	public LocalDate getEventDate() {
		return eventDate;
	}

	public void setEventDate(LocalDate eventDate) {
		this.eventDate = eventDate;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}


	
	
	
	
	
	
	
	

}

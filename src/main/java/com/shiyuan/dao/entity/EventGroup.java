package com.shiyuan.dao.entity;

import java.util.List;

public class EventGroup {
	
	String course;
	String eventDate;
	String eventDesc;
	String eventName;
	String season;	
	
	List<TeeObject> teeList;

	public List<TeeObject> getTeeList() {
		return teeList;
	}

	public void setTeeList(List<TeeObject> teeList) {
		this.teeList = teeList;
	}

	public String getCourse() {
		return course;
	}

	public void setCourse(String course) {
		this.course = course;
	}

	public String getEventDate() {
		return eventDate;
	}

	public void setEventDate(String eventDate) {
		this.eventDate = eventDate;
	}

	public String getEventDesc() {
		return eventDesc;
	}

	public void setEventDesc(String eventDesc) {
		this.eventDesc = eventDesc;
	}

	public String getEventName() {
		return eventName;
	}

	public void setEventName(String eventName) {
		this.eventName = eventName;
	}

	public String getSeason() {
		return season;
	}

	public void setSeason(String season) {
		this.season = season;
	}

	@Override
	public String toString() {
		return "EventGroup [course=" + course + ", eventDate=" + eventDate + ", eventDesc=" + eventDesc + ", eventName="
				+ eventName + ", season=" + season + ", teeList=" + teeList + "]";
	}

	

	
	
}

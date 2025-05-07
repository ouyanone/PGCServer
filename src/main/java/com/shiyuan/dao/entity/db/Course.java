package com.shiyuan.dao.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "Course")
public class Course {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "club_name")
	private String clubName;
	
	@Column(name = "course_name")
	private String courseName;
	
	@Column(name = "par_number")
	private Integer parNumber;
	
	@Column(name = "distance")
	private Integer distance;
	
	@Column(name = "slop")
	private Integer slop;
	
	@Column(name = "rating")
	private Double rating;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getClubName() {
		return clubName;
	}

	public void setClubName(String clubName) {
		this.clubName = clubName;
	}

	public String getCourseName() {
		return courseName;
	}

	public void setCourseName(String courseName) {
		this.courseName = courseName;
	}

	public Integer getParNumber() {
		return parNumber;
	}

	public void setParNumber(Integer parNumber) {
		this.parNumber = parNumber;
	}

	public Integer getDistance() {
		return distance;
	}

	public void setDistance(Integer distance) {
		this.distance = distance;
	}

	public Integer getSlop() {
		return slop;
	}

	public void setSlop(Integer slop) {
		this.slop = slop;
	}

	public Double getRating() {
		return rating;
	}

	public void setRating(Double rating) {
		this.rating = rating;
	}

	@Override
	public String toString() {
		return "Course [id=" + id + ", clubName=" + clubName + ", courseName=" + courseName + ", parNumber=" + parNumber
				+ ", distance=" + distance + ", slop=" + slop + ", rating=" + rating + "]";
	}
	


}

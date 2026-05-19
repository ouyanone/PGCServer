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

	@Column(name = "hole_1")  private Integer hole1;
	@Column(name = "hole_2")  private Integer hole2;
	@Column(name = "hole_3")  private Integer hole3;
	@Column(name = "hole_4")  private Integer hole4;
	@Column(name = "hole_5")  private Integer hole5;
	@Column(name = "hole_6")  private Integer hole6;
	@Column(name = "hole_7")  private Integer hole7;
	@Column(name = "hole_8")  private Integer hole8;
	@Column(name = "hole_9")  private Integer hole9;
	@Column(name = "hole_10") private Integer hole10;
	@Column(name = "hole_11") private Integer hole11;
	@Column(name = "hole_12") private Integer hole12;
	@Column(name = "hole_13") private Integer hole13;
	@Column(name = "hole_14") private Integer hole14;
	@Column(name = "hole_15") private Integer hole15;
	@Column(name = "hole_16") private Integer hole16;
	@Column(name = "hole_17") private Integer hole17;
	@Column(name = "hole_18") private Integer hole18;

	@Column(name = "distance")
	private Integer distance;
	
	@Column(name = "slop")
	private Integer slop;
	
	@Column(name = "rating")
	private Double rating;

	@Column(name = "address")
	private String address;

	@Column(name = "city")
	private String city;

	@Column(name = "state")
	private String state;

	@Column(name = "phone")
	private String phone;

	@Column(name = "website")
	private String website;

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

	public String getAddress() { return address; }
	public void setAddress(String address) { this.address = address; }

	public String getCity() { return city; }
	public void setCity(String city) { this.city = city; }

	public String getState() { return state; }
	public void setState(String state) { this.state = state; }

	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }

	public String getWebsite() { return website; }
	public void setWebsite(String website) { this.website = website; }

	public Integer getHole1()  { return hole1; }  public void setHole1(Integer v)  { hole1 = v; }
	public Integer getHole2()  { return hole2; }  public void setHole2(Integer v)  { hole2 = v; }
	public Integer getHole3()  { return hole3; }  public void setHole3(Integer v)  { hole3 = v; }
	public Integer getHole4()  { return hole4; }  public void setHole4(Integer v)  { hole4 = v; }
	public Integer getHole5()  { return hole5; }  public void setHole5(Integer v)  { hole5 = v; }
	public Integer getHole6()  { return hole6; }  public void setHole6(Integer v)  { hole6 = v; }
	public Integer getHole7()  { return hole7; }  public void setHole7(Integer v)  { hole7 = v; }
	public Integer getHole8()  { return hole8; }  public void setHole8(Integer v)  { hole8 = v; }
	public Integer getHole9()  { return hole9; }  public void setHole9(Integer v)  { hole9 = v; }
	public Integer getHole10() { return hole10; } public void setHole10(Integer v) { hole10 = v; }
	public Integer getHole11() { return hole11; } public void setHole11(Integer v) { hole11 = v; }
	public Integer getHole12() { return hole12; } public void setHole12(Integer v) { hole12 = v; }
	public Integer getHole13() { return hole13; } public void setHole13(Integer v) { hole13 = v; }
	public Integer getHole14() { return hole14; } public void setHole14(Integer v) { hole14 = v; }
	public Integer getHole15() { return hole15; } public void setHole15(Integer v) { hole15 = v; }
	public Integer getHole16() { return hole16; } public void setHole16(Integer v) { hole16 = v; }
	public Integer getHole17() { return hole17; } public void setHole17(Integer v) { hole17 = v; }
	public Integer getHole18() { return hole18; } public void setHole18(Integer v) { hole18 = v; }

	@Override
	public String toString() {
		return "Course [id=" + id + ", clubName=" + clubName + ", courseName=" + courseName + ", parNumber=" + parNumber
				+ ", distance=" + distance + ", slop=" + slop + ", rating=" + rating + "]";
	}
	


}

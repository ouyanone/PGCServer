package com.shiyuan.dao.entity;

public class Golfers {
	private String id;
	private String first_name;
	private String last_name;
	private String gender;
	private String status;
	private String handicap_index_display;
	
	
	
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getFirst_name() {
		return first_name;
	}
	public void setFirst_name(String first_name) {
		this.first_name = first_name;
	}
	public String getLast_name() {
		return last_name;
	}
	public void setLast_name(String last_name) {
		this.last_name = last_name;
	}
	public String getGender() {
		return gender;
	}
	public void setGender(String gender) {
		this.gender = gender;
	}

	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}

	public String getHandicap_index_display() {
		return handicap_index_display;
	}
	public void setHandicap_index_display(String handicap_index_display) {
		this.handicap_index_display = handicap_index_display;
	}
	@Override
	public String toString() {
		return "Golfers [id=" + id + ", first_name=" + first_name + ", last_name=" + last_name + ", gender=" + gender
				+ ", status=" + status + ", handicap_index_display=" + handicap_index_display + "]";
	}
	
	
	
	

}

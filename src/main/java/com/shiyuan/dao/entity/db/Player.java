package com.shiyuan.dao.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Player")
public class Player {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "first_name")
	private String fName;

	@Column(name = "last_name")
	private String lName;

	@Column(name = "ghin_number")
	private String ghinNumber;

	@Column(name = "phone")
	private String phone;

	@Column(name = "email")
	private String email;

	@Column(name = "nick_name")
	private String nickName;

	@Column(name = "nick_name_chinese")
	private String chineseNickName;

	@Column(name = "handicap")
	private double handicap;

	@Column(name = "club_id")
	private String clubId;

	@Column(name = "club_name")
	private String clubName;
	
	@Column(name = "icon")
	private String icon;
	
	
	@Column(name = "description")
	private String desc;
	
	@Column(name = "last_3_game_avg")
	private double last3GameAvg=0;
	
	@Column(name = "pgc_handicap")
	private Double pgcHandicap;
	

	@Column(name = "isPGC2025")
	private Boolean isPgc2025;
	
	@Column(name = "is_Active")
	private Boolean isActive;
	
	



	@Column(name = "level")
	private int level=0;
	
	
	
	
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getfName() {
		return fName;
	}

	public void setfName(String fName) {
		this.fName = fName;
	}

	public String getlName() {
		return lName;
	}

	public void setlName(String lName) {
		this.lName = lName;
	}

	public String getGhinNumber() {
		return ghinNumber;
	}

	public void setGhinNumber(String ghinNumber) {
		this.ghinNumber = ghinNumber;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getNickName() {
		return nickName;
	}

	public void setNickName(String nickName) {
		this.nickName = nickName;
	}

	public String getChineseNickName() {
		return chineseNickName;
	}

	public void setChineseNickName(String chineseNickName) {
		this.chineseNickName = chineseNickName;
	}

	public double getHandicap() {
		return handicap;
	}

	public void setHandicap(double handicap) {
		this.handicap = handicap;
	}

	public String getClubId() {
		return clubId;
	}

	public void setClubId(String clubId) {
		this.clubId = clubId;
	}

	public String getClubName() {
		return clubName;
	}

	public void setClubName(String clubName) {
		this.clubName = clubName;
	}

	public String getIcon() {
		return icon;
	}

	public void setIcon(String icon) {
		this.icon = icon;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public double getLast3GameAvg() {
		return last3GameAvg;
	}

	public void setLast3GameAvg(double last3GameAvg) {
		this.last3GameAvg = last3GameAvg;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public Double getPgcHandicap() {
		return pgcHandicap;
	}

	public void setPgcHandicap(Double pgcHandicap) {
		this.pgcHandicap = pgcHandicap;
	}

	public Boolean isPgc2025() {
		return isPgc2025;
	}

	public void setPgc2025(Boolean isPgc2025) {
		this.isPgc2025 = isPgc2025;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}
	
	@Override
	public String toString() {
		return "Player [id=" + id + ", fName=" + fName + ", lName=" + lName + ", ghinNumber=" + ghinNumber + ", phone="
				+ phone + ", email=" + email + ", nickName=" + nickName + ", chineseNickName=" + chineseNickName
				+ ", handicap=" + handicap + ", clubId=" + clubId + ", clubName=" + clubName + ", icon=" + icon
				+ ", desc=" + desc + ", last3GameAvg=" + last3GameAvg + ", level=" + level + "]";
	}


	

	
	
	

}

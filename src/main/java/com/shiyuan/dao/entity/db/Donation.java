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
@Table(name = "Donation")
public class Donation {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "donation_name")
	private String donationName;
	
	@Column(name = "donation_desc")
	private String donationDesc;	
	
	@Column(name = "donation_date")
	private LocalDate donationDate;
	
	@Column(name ="donor")
	private String donor;
	
	@Column(name = "cash")
	private int cash;
	
	@Column(name = "worth")
	private int worth;
	
	
	@Column(name ="product")
	private String product;
	
	@Column(name ="type")
	private String type;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getDonationName() {
		return donationName;
	}

	public void setDonationName(String donationName) {
		this.donationName = donationName;
	}

	public String getDonationDesc() {
		return donationDesc;
	}

	public void setDonationDesc(String donationDesc) {
		this.donationDesc = donationDesc;
	}

	public LocalDate getDonationDate() {
		return donationDate;
	}

	public void setDonationDate(LocalDate donationDate) {
		this.donationDate = donationDate;
	}

	public String getDonor() {
		return donor;
	}

	public void setDonor(String donor) {
		this.donor = donor;
	}

	public int getCash() {
		return cash;
	}

	public void setCash(int cash) {
		this.cash = cash;
	}

	public int getWorth() {
		return worth;
	}

	public void setWorth(int worth) {
		this.worth = worth;
	}

	public String getProduct() {
		return product;
	}

	public void setProduct(String product) {
		this.product = product;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	
	
	
	
	
	

}

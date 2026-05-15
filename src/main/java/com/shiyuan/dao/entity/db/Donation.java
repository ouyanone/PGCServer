package com.shiyuan.dao.entity.db;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "donation")
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

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "donation_player")
	private Player player;

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

	public Player getPlayer() {
		return player;
	}

	public void setPlayer(Player player) {
		this.player = player;
	}

	@Column(name = "amount")
	private BigDecimal amount;

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}
}

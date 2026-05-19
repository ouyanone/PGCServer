package com.shiyuan.dao.entity.db;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "Reward")
public class Reward {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(name = "reward_name")
	private String rewardName;
	@Column(name = "reward_desc")
	private String rewardDesc;
	@Column(name = "reward_story")
	private String rewardStory;
	@Column(name = "display_order")
	private Integer displayOrder;
	@Column(name = "reward_group")
	private String rewardGroup;
	
	
	@ManyToOne
	@JoinColumn(name="event_id")
	@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"teeList","player","season"})
	private Event event;

	@ManyToOne
	@JoinColumn(name="player_id")
	private Player player;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getRewardName() {
		return rewardName;
	}

	public void setRewardName(String rewardName) {
		this.rewardName = rewardName;
	}

	public String getRewardDesc() {
		return rewardDesc;
	}

	public void setRewardDesc(String rewardDesc) {
		this.rewardDesc = rewardDesc;
	}

	public String getRewardStory() {
		return rewardStory;
	}

	public void setRewardStory(String rewardStory) {
		this.rewardStory = rewardStory;
	}

	public Event getEvent() {
		return event;
	}

	public void setEvent(Event event) {
		this.event = event;
	}

	public Player getPlayer() {
		return player;
	}

	public void setPlayer(Player player) {
		this.player = player;
	}

	public Integer getDisplayOrder() {
		return displayOrder;
	}

	public void setDisplayOrder(Integer displayOrder) {
		this.displayOrder = displayOrder;
	}

	public String getRewardGroup() {
		return rewardGroup;
	}

	public void setRewardGroup(String rewardGroup) {
		this.rewardGroup = rewardGroup;
	}

}

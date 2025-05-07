package com.shiyuan.service;

import java.util.List;

import com.shiyuan.dao.entity.EventGroup;
import com.shiyuan.dao.entity.GhinUser;
import com.shiyuan.dao.entity.db.Donation;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.ScoreStatistic;

public interface PlayerService {
	List<Player> getAllPlayers();
	
	void syncHandicap(Long id, String bearerToken) throws Exception;

	Player savePlayer(Player player);

	Player getPlayerById(String id);

	void createEvent(EventGroup eventGroup);

	List<Event> getAllEvents();

	Event getEventById(String id);

	void submitScore(Event event);

	void deleteEventById(String id);

	void updateLast3Score();

	List<Donation> getAllDonations();

	Event getUpcomingEvent();

	List<Event> getLatestEvents();

	

}

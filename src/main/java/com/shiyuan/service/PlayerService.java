package com.shiyuan.service;

import java.util.List;

import com.shiyuan.dao.entity.EventGroup;
import com.shiyuan.dao.entity.GhinUser;
import com.shiyuan.dao.entity.PlayerGolfScore;
import com.shiyuan.dao.entity.PlayerGolfScoreEntrys;
import com.shiyuan.dao.entity.db.Donation;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.EventUser;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.entity.db.Reward;
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

	List<EventUser> onboardEventUser(String eventId, List<EventUser> eventUsers);

	List<Reward> findRewardForEvent(Long id);
	
	List<PlayerScore> findPlayerScoreForEvent(Long eventId);

	void submitPlayerScore(List<PlayerGolfScore> scores);

	void addEventPlayer(List<PlayerGolfScoreEntrys> scorePlayerEntrys);
	

}

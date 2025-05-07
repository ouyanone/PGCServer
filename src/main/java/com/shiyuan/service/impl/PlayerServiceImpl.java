package com.shiyuan.service.impl;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.shiyuan.dao.entity.EventGroup;
import com.shiyuan.dao.entity.GhinUser;
import com.shiyuan.dao.entity.Golfers;
import com.shiyuan.dao.entity.TeeObject;
import com.shiyuan.dao.entity.db.Course;
import com.shiyuan.dao.entity.db.Donation;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.entity.db.ScoreStatistic;
import com.shiyuan.dao.entity.db.Season;
import com.shiyuan.dao.entity.db.Team;
import com.shiyuan.dao.entity.db.Tee;
import com.shiyuan.dao.entity.db.TeeTeamXref;
import com.shiyuan.dao.repository.CourseRepository;
import com.shiyuan.dao.repository.DonationRepository;
import com.shiyuan.dao.repository.EventRepository;
import com.shiyuan.dao.repository.PlayerRepository;
import com.shiyuan.dao.repository.PlayerScoreRepository;
import com.shiyuan.dao.repository.SeasonRepository;
import com.shiyuan.dao.repository.TeamRepository;
import com.shiyuan.dao.repository.TeeRepository;
import com.shiyuan.dao.repository.TeeTeamXrefRepository;
import com.shiyuan.service.PlayerService;

@Service
public class PlayerServiceImpl implements PlayerService {

	@Autowired
	PlayerRepository playerRepository;

	@Autowired
	CourseRepository courseRepository;

	@Autowired
	SeasonRepository seasonRepository;

	@Autowired
	TeamRepository teamRepository;

	@Autowired
	TeeRepository teeRepository;

	@Autowired
	TeeTeamXrefRepository teeTeamXrefRepository;

	@Autowired
	PlayerScoreRepository playerScoreRepository;

	@Autowired
	EventRepository eventRepository;
	

	@Autowired
	DonationRepository donationRepository;

	@Autowired
	RestTemplate restTemplate;

	// private String
	// GHIN_URL="https://api.ghin.com/api/v1/clubs/10138/golfers/11916718/handicap_display.json";

	private String GHIN_URL = "https://api2.ghin.com/api/v1/golfers.json?status=Active&from_ghin=true&per_page=25&page=1&golfer_id={{ghinId}}&includeLowHandicapIndex=true&source=GHINcom";

	private String MY_GHIN_FOLLOWED_GOLFER_URL = "https://api2.ghin.com/api/v1/followed_golfers/11916718.json?source=GHINcom";

	@Override
	public List<Player> getAllPlayers() {
		Iterable<Player> pi = playerRepository.findAll();
		List<Player> pList = new ArrayList<Player>();

		pi.forEach(pList::add);
		return pList;

	}

	@Override
	public void syncHandicap(Long id, String bearerToken) throws Exception {

		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
		headers.setBearerAuth(bearerToken);

		if (id == null) {
			// sync all player's ghin

			ResponseEntity<GhinUser> rsp = restTemplate.exchange(
					RequestEntity.get(new URI(MY_GHIN_FOLLOWED_GOLFER_URL)).headers(headers).build(), GhinUser.class);
			GhinUser guser = rsp.getBody();
			System.out.println("ghinuser=" + guser);
			for (int i = 0; i < guser.getGolfers().length; i++) {
				Golfers golfer = guser.getGolfers()[i];
				try {

					Player p = playerRepository.findFirstByGhinNumber(golfer.getId());
					if (p==null) {
						continue;
					}
					p.setHandicap(Double.parseDouble(golfer.getHandicap_index_display()));
					playerRepository.save(p);
					System.out.println("gofer:" + p.getfName() + " " + p.getlName() + " handicap to::"
							+ (golfer.getHandicap_index_display()));
				} catch (NumberFormatException e) {
					// TODO Auto-generated catch block
					System.out.println("could not sync handicap for gofer:" + golfer.getFirst_name() + " "
							+ golfer.getFirst_name());
					e.printStackTrace();
				}
			}

			return;
		} else {

			// TODO: to query one golfer's information
			// only sync one player's ghin

			// headers.setContentType(MediaType.APPLICATION_JSON);

			GhinUser gu = new GhinUser();

			HttpEntity<GhinUser> entity = new HttpEntity<GhinUser>(gu, headers);

			ResponseEntity<GhinUser> rsp = restTemplate.exchange(GHIN_URL, HttpMethod.GET, entity, GhinUser.class);
			// ResponseEntity<GhinUser> rsp = restTemplate.exchange(RequestEntity.get(new
			// URI(GHIN_URL)).headers(headers).build(), GhinUser.class);
			// return rsp.getBody();

		}
	}

	@Override
	public Player savePlayer(Player player) {
		return playerRepository.save(player);
	}

	@Override
	public Player getPlayerById(String id) {
		return playerRepository.findById(Long.parseLong(id)).get();
	}

	@Override
	public void createEvent(EventGroup eventGroup) {
		// TODO Auto-generated method stub
		Event event = new Event();
		event.setStatus("OPEN");
		event.setEventName(eventGroup.getEventName());
		event.setEventDesc(eventGroup.getEventDesc());
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("E MMM dd yyyy");
		LocalDate date = LocalDate.parse(eventGroup.getEventDate(), formatter);
		event.setEventDate(date);
		event.setEventStory("no story");

		Season season = seasonRepository.findFirstBySeasonName(eventGroup.getSeason());
		System.out.println("season=" + season);
		// event.setCourse(null);
		event.setSeason(season);

		Course course = courseRepository.findById(Long.parseLong(eventGroup.getCourse())).orElse(null);
		event.setCourse(course);
		List<Tee> teeList = new ArrayList<>();
		for (TeeObject teeObject : eventGroup.getTeeList()) {
			Tee tee = new Tee();
			tee.setTeeName(teeObject.getTeeName());
			tee.setTeeDesc(null);
			tee.setTeeStroy("no story");
			tee.setCourse(course);
			tee.setEvent(event);
			tee.setTeeTime(teeObject.getTeeTime());
			List<TeeTeamXref> teeTeamXrefList = new ArrayList<>();

			TeeTeamXref teeTeamXrefA = new TeeTeamXref();
			Team teamA = teamRepository.findById(1L).orElse(null);
			teeTeamXrefA.setTeam(teamA);
			teeTeamXrefA.setTee(tee);
			teeTeamXrefList.add(teeTeamXrefA);
			List<PlayerScore> psListA = new ArrayList<>();
			for (Player player : teeObject.getTeamA()) {
				PlayerScore ps = new PlayerScore();
				ps.setEntryScore(player.getLast3GameAvg());
				ps.setPlayer(player);
				ps.setTeeTeamXref(teeTeamXrefA);
				ps.setScoreDate(date);
				psListA.add(ps);
			}
			teeTeamXrefA.setPlayScoreList(psListA);

			TeeTeamXref teeTeamXrefB = new TeeTeamXref();
			Team teamB = teamRepository.findById(2L).orElse(null);
			teeTeamXrefB.setTeam(teamB);
			teeTeamXrefB.setTee(tee);
			teeTeamXrefList.add(teeTeamXrefB);

			List<PlayerScore> psListB = new ArrayList<>();
			for (Player player : teeObject.getTeamB()) {
				PlayerScore ps = new PlayerScore();
				ps.setEntryScore(player.getLast3GameAvg());
				ps.setPlayer(player);
				ps.setTeeTeamXref(teeTeamXrefB);
				ps.setScoreDate(date);
				psListB.add(ps);
			}
			teeTeamXrefB.setPlayScoreList(psListB);

			teeTeamXrefList.add(teeTeamXrefA);
			teeTeamXrefList.add(teeTeamXrefB);

			tee.setTeeTeamXrefList(teeTeamXrefList);

			teeList.add(tee);

		}
		event.setTeeList(teeList);
		System.out.println("teeList==");
		System.out.println("teeList==" + teeList);
		eventRepository.save(event);

	}

	@Override
	public List<Event> getAllEvents() {
		Iterable<Event> ei = eventRepository.findAllByOrderByEventDateDesc();
		List<Event> eList = new ArrayList<Event>();
		ei.forEach(eList::add);
		for (Event event: eList) {
			LocalDate eventDate = event.getEventDate();
			String course = event.getCourse().getCourseName();
			String club = event.getCourse().getClubName();
			List<Tee> teeList = event.getTeeList();
			for (Tee tee: teeList) {
				
				String teeName = tee.getTeeName();
				for(TeeTeamXref ttXref: tee.getTeeTeamXrefList()) {
					List<PlayerScore> playerScoreList = ttXref.getPlayScoreList();
					for (PlayerScore playerScore: playerScoreList ) {
						Player player = playerScore.getPlayer();
						Double entryScore = playerScore.getEntryScore();
						Integer score = playerScore.getScore();
						
						
						System.out.println("On "+eventDate+", Golf player "+ player.getfName()+" "+ player.getlName()+ " played at "+club +" "+course+" course with entry score of "+entryScore+ " and the actual score was "+score+"."+ teeMsg(playerScore.getTeeWin()) + teamMsg(playerScore.getTeamWin())  );
						
					}
					
					
				}
			}
			
			
			
		}
		
		return eList;
	}
	
	private String teeMsg(Integer win) {
		if (win==null) return null;
		if (win==1) return "He has won the game on the tee. ";
		else if (win==-1) return "He has lost the game on the tee. ";
		else return "He has tie the game on the tee. ";
		
	}
	
	private String teamMsg(Integer win) {
		if (win==null) return null;
		if (win==1) return "His team has won the game. ";
		else if (win==-1) return "His team has lost the game. ";
		else return "His team has tie the game. ";
		
	}

	@Override
	public Event getEventById(String strId) {
		Long id = Long.parseLong(strId);
		Event event = eventRepository.findById(id).orElse(null);
	
		return event;
	}

	@Override
	public void submitScore(Event event) {
		List<Tee> teeList = event.getTeeList();
		int teamAScore = 0;
		for (Tee tee : teeList) {
			// each tee
			tee.setEvent(event);
			List<TeeTeamXref> teeTeamXrefList = tee.getTeeTeamXrefList();
			teeTeamXrefList.get(1).setScore(teeTeamXrefList.get(0).getScore() * -1);

			for (TeeTeamXref teeTeamXref : teeTeamXrefList) {
				// each team on the same tee
				teeTeamXref.setTee(tee);
				System.out.println("teeTeamScore::" + teeTeamXref.getScore());
				System.out.println("teeTeamXref.getTeam().getId()"+teeTeamXref.getTeam().getId());
				System.out.println(teeTeamXref.getTeam().getTeamName().equals("A"));
				if (teeTeamXref.getTeam().getTeamName().equals("A")) {
					teamAScore += teeTeamXref.getScore();
					System.out.println("-----teamAScore::"+teamAScore);
				}

				List<PlayerScore> playerScoreList = teeTeamXref.getPlayScoreList();
				for (PlayerScore playerScore : playerScoreList) {
					System.out.println("player Score::" + playerScore.getScore());
					playerScore.setTeeTeamXref(teeTeamXref);
					if (teeTeamXref.getScore() > 0) {
						playerScore.setTeeWin(1);
					} else if (teeTeamXref.getScore() == 0) {
						playerScore.setTeeWin(0);
					} else {
						playerScore.setTeeWin(-1);
					}
				}
			}

		}

		for (Tee tee : teeList) {
			// each tee
			List<TeeTeamXref> teeTeamXrefList = tee.getTeeTeamXrefList();
			teeTeamXrefList.get(1).setScore(teeTeamXrefList.get(0).getScore() * -1);
			int teamAWin = 0;
			for (TeeTeamXref teeTeamXref : teeTeamXrefList) {
				// each team on the same tee
				System.out.println("teeTeamScore::" + teeTeamXref.getScore());
				boolean isTeamA = false;
				
				if (teeTeamXref.getTeam().getTeamName().equals("A")) {
					isTeamA = true;
					if (teamAScore > 0) {
						teamAWin = 1;
					} else if (teamAScore == 0) {
						teamAWin = 0;
					} else {
						teamAWin = -1;
					}
				}
				List<PlayerScore> playerScoreList = teeTeamXref.getPlayScoreList();
				for (PlayerScore playerScore : playerScoreList) {

					if (isTeamA) {
						playerScore.setTeamWin(teamAWin);
					} else {
						playerScore.setTeamWin(teamAWin * -1);
					}

				}
			}

		}
		event.setStatus("CLOSED");
		eventRepository.save(event);

	}

	@Override
	public void deleteEventById(String strId) {
		Long id = Long.parseLong(strId);
		eventRepository.deleteById(id);
		
	}

	@Override
	public void updateLast3Score() {
		Iterable<Player> pi = playerRepository.findAll();
		List<Player> pList = new ArrayList<Player>();
		pi.forEach(pList::add);
		
		for (Player player: pList) {
			List<PlayerScore> psList = playerScoreRepository.getLast3Records(player.getId());
			int numOfScore = psList.size();
			if (numOfScore>0) {
				System.out.println("updateing last3Score for ::" + player.getfName());
				int sum = psList.stream().mapToInt(PlayerScore::getScore).sum();
				double avgScore = sum/numOfScore;
				avgScore = Math.round(avgScore * 100.0) / 100.0;
				
				System.out.println(player.getfName()+ " updateing last3Score to ::" + avgScore);
				player.setLast3GameAvg(avgScore);
				playerRepository.save(player);
			}
			
			
			
			
			
		}
		
	}

	@Override
	public List<Donation> getAllDonations() {
		Iterable<Donation> di = donationRepository.findAllByOrderByDonationDateDesc();
		List<Donation> dList = new ArrayList<Donation>();
		di.forEach(dList::add);
		return dList;
	}

	@Override
	public Event getUpcomingEvent() {
		LocalDate now = LocalDate.now();
		Event event = eventRepository.findFirstByStatusAndEventDateGreaterThanEqualOrderByEventDate("OPEN", now);
		
		return event;
	}

	@Override
	public List<Event> getLatestEvents() {
		Iterable<Event> ei = eventRepository.findFirst3ByStatusOrderByEventDateDesc("CLOSED");
		List<Event> eList = new ArrayList<Event>();
		ei.forEach(eList::add);
		for (Event event: eList) {
			LocalDate eventDate = event.getEventDate();
			String course = event.getCourse().getCourseName();
			String club = event.getCourse().getClubName();
			List<Tee> teeList = event.getTeeList();
			for (Tee tee: teeList) {
				
				String teeName = tee.getTeeName();
				for(TeeTeamXref ttXref: tee.getTeeTeamXrefList()) {
					List<PlayerScore> playerScoreList = ttXref.getPlayScoreList();
					for (PlayerScore playerScore: playerScoreList ) {
						Player player = playerScore.getPlayer();
						Double entryScore = playerScore.getEntryScore();
						Integer score = playerScore.getScore();
						
						
						System.out.println("On "+eventDate+", Golf player "+ player.getfName()+" "+ player.getlName()+ " played at "+club +" "+course+" course with entry score of "+entryScore+ " and the actual score was "+score+"."+ teeMsg(playerScore.getTeeWin()) + teamMsg(playerScore.getTeamWin())  );
						
					}
					
					
				}
			}
			
			
			
		}
		
		return eList;
	}



}



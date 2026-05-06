package com.shiyuan.service.impl;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.shiyuan.dao.entity.EventGroup;
import com.shiyuan.dao.entity.GhinUser;
import com.shiyuan.dao.entity.Golfers;
import com.shiyuan.dao.entity.PlayerGolfScore;
import com.shiyuan.dao.entity.PlayerGolfScoreEntrys;
import com.shiyuan.dao.entity.TeeObject;
import com.shiyuan.dao.entity.db.Course;
import com.shiyuan.dao.entity.db.Donation;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.EventUser;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.entity.db.Reward;
import com.shiyuan.dao.entity.db.Season;
import com.shiyuan.dao.entity.db.Team;
import com.shiyuan.dao.entity.db.Tee;
import com.shiyuan.dao.repository.CourseRepository;
import com.shiyuan.dao.repository.DonationRepository;
import com.shiyuan.dao.repository.EventRepository;
import com.shiyuan.dao.repository.PlayerRepository;
import com.shiyuan.dao.repository.PlayerScoreRepository;
import com.shiyuan.dao.repository.RewardRepository;
import com.shiyuan.dao.repository.SeasonRepository;
import com.shiyuan.dao.repository.TeamRepository;
import com.shiyuan.dao.repository.TeeRepository;
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
	RewardRepository rewardRepository;
	
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
		Iterable<Player> pi = playerRepository.findByIsActive(true);
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
		/*
		 * // TODO Auto-generated method stub Event event = new Event();
		 * event.setStatus("OPEN"); event.setEventName(eventGroup.getEventName());
		 * event.setEventDesc(eventGroup.getEventDesc()); DateTimeFormatter formatter =
		 * DateTimeFormatter.ofPattern("E MMM dd yyyy"); LocalDate date =
		 * LocalDate.parse(eventGroup.getEventDate(), formatter);
		 * event.setEventDate(date); event.setEventStory("no story");
		 * 
		 * Season season =
		 * seasonRepository.findFirstBySeasonName(eventGroup.getSeason());
		 * System.out.println("season=" + season); // event.setCourse(null);
		 * event.setSeason(season);
		 * 
		 * Course course =
		 * courseRepository.findById(Long.parseLong(eventGroup.getCourse())).orElse(null
		 * ); event.setCourse(course); List<Tee> teeList = new ArrayList<>(); for
		 * (TeeObject teeObject : eventGroup.getTeeList()) { Tee tee = new Tee();
		 * tee.setTeeName(teeObject.getTeeName()); tee.setTeeDesc(null);
		 * tee.setTeeStroy("no story"); //tee.setCourse(course); tee.setEvent(event);
		 * tee.setTeeTime(teeObject.getTeeTime());
		 * 
		 * Team teamA = teamRepository.findById(1L).orElse(null);
		 * 
		 * List<PlayerScore> psListA = new ArrayList<>(); for (Player player :
		 * teeObject.getTeamA()) { PlayerScore ps = new PlayerScore();
		 * ps.setEntryScore(player.getLast3GameAvg()); ps.setPlayer(player);
		 * //ps.setTeeTeamXref(teeTeamXrefA); ps.setScoreDate(date); psListA.add(ps); }
		 * //teeTeamXrefA.setPlayScoreList(psListA);
		 * 
		 * Team teamB = teamRepository.findById(2L).orElse(null);
		 * 
		 * 
		 * List<PlayerScore> psListB = new ArrayList<>(); for (Player player :
		 * teeObject.getTeamB()) { PlayerScore ps = new PlayerScore();
		 * ps.setEntryScore(player.getLast3GameAvg()); ps.setPlayer(player);
		 * //ps.setTeeTeamXref(teeTeamXrefB); ps.setScoreDate(date); psListB.add(ps); }
		 * //teeTeamXrefB.setPlayScoreList(psListB);
		 * 
		 * 
		 * teeList.add(tee);
		 * 
		 * } event.setTeeList(teeList); System.out.println("teeList==");
		 * System.out.println("teeList==" + teeList); eventRepository.save(event);
		 * 
		 */}

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
			

	

		}

		for (Tee tee : teeList) {}
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
		Event event = eventRepository.findFirstByStatusAndEventDateGreaterThanEqualOrderByEventDate("INIT", now);
		
		return event;
	}

	@Override
	public List<Event> getLatestEvents() {
		Iterable<Event> ei = eventRepository.findFirst3ByStatusOrderByEventDateDesc("FINISHED");
		List<Event> eList = new ArrayList<Event>();
		ei.forEach(eList::add);
		for (Event event: eList) {
			LocalDate eventDate = event.getEventDate();
			String course = event.getCourse().getCourseName();
			String club = event.getCourse().getClubName();
			List<Tee> teeList = event.getTeeList();
			for (Tee tee: teeList) {
				
				String teeName = tee.getTeeName();
			}
			
			
			
		}
		
		return eList;
	}

	@Override
	public List<EventUser> onboardEventUser(String eventId, List<EventUser> eventUsers) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Reward> findRewardForEvent(Long id) {
		List<Reward> rewards = rewardRepository.findRewardsByEventId(id);
		Collections.sort(rewards, Comparator.comparing(Reward::getDisplayOrder));
		return rewards;
	
	}

	@Override
	public List<PlayerScore> findPlayerScoreForEvent(Long eventId) {
		List<PlayerScore> playerScores = playerScoreRepository.getPlayerScoreForEvent(eventId);
		/*
		 * for(PlayerScore ps: playerScores) { ps.setNetScore(ps.getScore() -
		 * ps.getPlayer().getPgcHandicap()); }
		 */
		
		return playerScores;
	}

	@Override
	public void submitPlayerScore(List<PlayerGolfScore> scores) {
		//find the event in init stage
		Event currentInitEvent = eventRepository.findFirstByStatus("INIT");
		System.out.println("currentInitEvent::"+currentInitEvent);
		
		List<PlayerScore> psEntityList = playerScoreRepository.getPlayerScoreForEvent(currentInitEvent.getId());
		int i=0;
		for (PlayerGolfScore ps: scores ) {
			i++;
			String fname="";
			String lname="";
			String name = ps.getName();

			try {
				String [] nm =name.split(" ");
				if (nm.length>1) {
					fname = nm[0];
					lname = nm[1];
				} else {
					fname=nm[0];
				}
				
			 System.out.println(i+" fname: "+fname +" lname: "+lname);
			 //psEntityList.stream().filter(pscore -> pscore.getPlayer().getfName().equalsIgnoreCase(fname)).collect(Collectors.toList());
			 for (PlayerScore psEntity: psEntityList) {
				 if (psEntity.getPlayer().getfName().equalsIgnoreCase(fname)&&psEntity.getPlayer().getlName().equalsIgnoreCase(lname)) {
					 
					 if (ps.getH1()!=null) psEntity.setHole1(getHoleScore(ps.getH1()));
					 if (ps.getH2()!=null) psEntity.setHole2(getHoleScore(ps.getH2()));
					 if (ps.getH3()!=null) psEntity.setHole3(getHoleScore(ps.getH3()));
					 if (ps.getH4()!=null) psEntity.setHole4(getHoleScore(ps.getH4()));
					 if (ps.getH5()!=null) psEntity.setHole5(getHoleScore(ps.getH5()));
					 if (ps.getH6()!=null) psEntity.setHole6(getHoleScore(ps.getH6()));
					 if (ps.getH7()!=null) psEntity.setHole7(getHoleScore(ps.getH7()));
					 if (ps.getH8()!=null) psEntity.setHole8(getHoleScore(ps.getH8()));
					 if (ps.getH9()!=null) psEntity.setHole9(getHoleScore(ps.getH9()));
					 if (ps.getH10()!=null) psEntity.setHole10(getHoleScore(ps.getH10()));
					 if (ps.getH11()!=null) psEntity.setHole11(getHoleScore(ps.getH11()));
					 if (ps.getH12()!=null) psEntity.setHole12(getHoleScore(ps.getH12()));
					 if (ps.getH13()!=null) psEntity.setHole13(getHoleScore(ps.getH13()));
					 if (ps.getH14()!=null) psEntity.setHole14(getHoleScore(ps.getH14()));
					 if (ps.getH15()!=null) psEntity.setHole15(getHoleScore(ps.getH15()));
					 if (ps.getH16()!=null) psEntity.setHole16(getHoleScore(ps.getH16()));
					 if (ps.getH17()!=null) psEntity.setHole17(getHoleScore(ps.getH17()));
					 if (ps.getH18()!=null) psEntity.setHole18(getHoleScore(ps.getH18()));
					 
					 if (ps.getScore()!=null) psEntity.setScore(getHoleScore(ps.getScore()));
					 
					 if (psEntity.getEntryPGCHandicap()==null) { 
						 psEntity.setEntryPGCHandicap(psEntity.getPlayer().getPgcHandicap());
					 }
					 //formula to calculate handicap index: (Adjusted Gross Score - Course Rating - PCC) x (113 / Slope Rating). 
						if (ps.getScore() != null) {
							double SLOPE_RATING=126.0;
							double COURSE_RATING = 73.4;
							double rt = 113.0 / SLOPE_RATING;
							System.out.println("rt :: "+rt);
							Double currentRoundhandicap = (Double.valueOf(ps.getScore()) - COURSE_RATING)*rt;
							System.out.println("currentRoundhandicap :: "+currentRoundhandicap);
							psEntity.setCurrentRoundHandicap(currentRoundhandicap);
							if (psEntity.getEntryPGCHandicap()!=null) psEntity.setNetScore(psEntity.getScore() - psEntity.getEntryPGCHandicap());
							
							psEntity.getPlayer().setPgcHandicap((currentRoundhandicap+psEntity.getEntryPGCHandicap()) / 2);
						}
					 
					 System.out.println("save score for :: "+name);
					 
					 playerScoreRepository.save(psEntity);
					 
				 } 
			 }
			 
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("the user with name could not be processed:: "+name);
				continue;
			}
			
			
			
			
		}
		
		
	}
	
	private Integer getHoleScore(String offSet) {
		Integer returnVal = null;
		if(StringUtils.isEmpty(offSet)) {
			return null;
		} else {
			returnVal = Integer.valueOf(offSet);
		}
		return returnVal;
	}

	@Override
	public void addEventPlayer(List<PlayerGolfScoreEntrys> scorePlayerEntrys) {
		
		// get all players
		Iterable<Player> pi = playerRepository.findAll();
		List<Player> pList = new ArrayList<Player>();
		pi.forEach(pList::add);

		for (PlayerGolfScoreEntrys pse : scorePlayerEntrys) {
			String fname = "";
			String lname = "";
			String name = pse.getName();

			try {
				String[] nm = name.split(" ");
				if (nm.length > 1) {
					fname = nm[0];
					lname = nm[1];
				} else {
					fname = nm[0];
				}

				System.out.println(" fname: " + fname + " lname: " + lname);
				outerLoop: 
				for (Player player : pList) {
					if (player.getfName().equalsIgnoreCase(fname) && player.getlName().equalsIgnoreCase(lname)) {
						PlayerScore ps = new PlayerScore();
						ps.setPlayer(player);
						ps.setEntryPGCHandicap(player.getPgcHandicap());
						
						Tee tee = new Tee();
						tee.setId(10000L);
						ps.setTee(tee);
						
						//check if the playerscore exist
						List<PlayerScore> psList = playerScoreRepository.findByTeeId(10000L);
						if(psList!=null&&psList.size()>0) {
							for (PlayerScore ps1: psList) {
								if (ps1.getPlayer().getfName().equalsIgnoreCase(fname)&&ps1.getPlayer().getlName().equalsIgnoreCase(lname)) {
									break outerLoop;
								}
							}
							
							
						}
						
						playerScoreRepository.save(ps);
						
						

					}
				}

			} catch (Exception e) {

			}

		}
	}

	/*
	 * @Override public List<EventUser> onboardEventUser(String eventId,
	 * List<EventUser> eventUsers) { List<EventUser> eventUsersAdded; for (EventUser
	 * user : eventUsers) { String id = user.getId(); String weChatName =
	 * user.getWeChatName(); Player player =
	 * playerRepository.findByNickName(weChatName.trim()); PlayerScore playerScore =
	 * playerScoreRepository.findFirstByPalyerId(player.getId()); if
	 * (playerScore==null) { PlayerScore ps = new PlayerScore();
	 * ps.setPlayer(player); Tee tee =
	 * teeRepository.findByEventIdAndTeeName(eventId, ""); ps.setTee(tee);
	 * playerScoreRepository.save(ps); eventUsersAdded.add(user); }
	 * 
	 * } return eventUsersAdded; }
	 */



}



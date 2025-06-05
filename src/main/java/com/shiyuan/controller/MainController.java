package com.shiyuan.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Calendar;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shiyuan.dao.entity.AccessToken;
import com.shiyuan.dao.entity.EventGroup;
import com.shiyuan.dao.entity.db.Donation;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.EventUser;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.ScoreStatistic;
import com.shiyuan.service.PlayerService;

@RestController
public class MainController {
	
	private final static Logger log = LoggerFactory.getLogger(MainController.class);
	
	@Autowired
	PlayerService playerService;
	
	String UPLOAD_DIR = "/myApp/apache-tomcat-10.1.30/photo";
	
	//String UPLOAD_DIR = "photo111";
	

	
/*
 * @CrossOrigin("*")
 * 
 * @GetMapping(path = "/api/oauth/callback",
 * produces=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<Object>
 * callback(Principal principal) throws Exception {
 * 
 * OAuth2AuthorizationRequest.Builder.build()
 * 
 * return new ResponseEntity<Object>(null, HttpStatus.OK); }
 */
	


	@CrossOrigin("*")
	@GetMapping(path = "/webapi/players", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> getAllPlayers(@AuthenticationPrincipal OidcUser principal) throws Exception {
		
		
		List<Player> pList = playerService.getAllPlayers();
		pList.forEach((p) -> System.out.println(p));
		System.out.println("pList ======  "+pList.size());
		//System.out.println(principal.getName());
		//System.out.println(principal.getGivenName());
		//System.out.println(principal.getAccessTokenHash());
		//System.out.println(principal.getProfile());
		//GhinUser gUser = playerService.syncHandicap(123L, "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIzMjExMjM0Iiwic2NwIjoidXNlciIsImF1ZCI6bnVsbCwiaWF0IjoxNzA4NDYxNDk0LCJleHAiOjE3MDg1MDQ2OTQsImp0aSI6IjkwZWE5NjQ4LWE3MWYtNDRlNC04YWVhLTcwMDlhYWFkMGE1OSJ9.mCWR09u1sR4_FxiH0XTRRCaFfU9oWunEgrApwloNIRE");
		//System.out.println("gUser ======  "+gUser.getGolfers().getFirst_name());
		return new ResponseEntity<Object>(pList, HttpStatus.OK);
	}
	
	
	@CrossOrigin("*")
	@GetMapping(path = "/webapi/players/{id}", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> getPlayerbyId(@PathVariable("id") String id) throws Exception {
		Player p = playerService.getPlayerById(id);

		
		//GhinUser gUser = playerService.syncHandicap(123L, "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIzMjExMjM0Iiwic2NwIjoidXNlciIsImF1ZCI6bnVsbCwiaWF0IjoxNzA4NDYxNDk0LCJleHAiOjE3MDg1MDQ2OTQsImp0aSI6IjkwZWE5NjQ4LWE3MWYtNDRlNC04YWVhLTcwMDlhYWFkMGE1OSJ9.mCWR09u1sR4_FxiH0XTRRCaFfU9oWunEgrApwloNIRE");
		//System.out.println("gUser ======  "+gUser.getGolfers().getFirst_name());
		return new ResponseEntity<Object>(p, HttpStatus.OK);
	}
	
	@CrossOrigin("*")
	@PostMapping(path = "/webapi/admin/players", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Player> createPalyer(@RequestBody Player player, @AuthenticationPrincipal OidcUser principal) throws Exception {
		log.debug("input player="+player);
		System.out.println("input player="+player);
		//System.out.println(principal.getName());
		//System.out.println(principal.getProfile());
		checkPrincipal(principal);
		
		Player savedPlayer = playerService.savePlayer(player);
		return new ResponseEntity<Player>(savedPlayer, HttpStatus.OK);
	}
	
	private void checkPrincipal(OidcUser principal) throws Exception {
		if (principal!=null&&!"admin".equalsIgnoreCase(principal.getProfile())) {
			throw new Exception("You donnot have privilidge to do this operation. Please ask Hexin to fix it.");
		}
		
	}


	@CrossOrigin("*")
	@PostMapping(path = "/webapi/players/ghin", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> updatePalyerGhin(@RequestBody AccessToken token) throws Exception {
		log.debug("input token="+token);
		System.out.println("input token="+token);
		
		playerService.syncHandicap(null, token.getToken());
		return new ResponseEntity<Object>(null, HttpStatus.OK);
	}
	
	
	@CrossOrigin("*")
	@PostMapping(path = "/webapi/admin/game/grouping", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> creatEventGroup(@RequestBody EventGroup eventGroup, @AuthenticationPrincipal OidcUser principal) throws Exception {
		log.debug("input eventGroup="+eventGroup);
		System.out.println("input eventGroup="+eventGroup);
		checkPrincipal(principal);
		playerService.createEvent(eventGroup);
		return new ResponseEntity<Object>(null, HttpStatus.OK);
	}
	
	@CrossOrigin("*")
	@GetMapping(path = "/webapi/events", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> getAllEvents() throws Exception {
		List<Event> eList = playerService.getAllEvents();
		//eList.forEach((p) -> p.setTeeList(null));
		System.out.println("pList ======  "+eList.size());
		
		return new ResponseEntity<Object>(eList, HttpStatus.OK);
	}
	
	@CrossOrigin("*")
	@GetMapping(path = "/webapi/events/{id}", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> getEventsById(@PathVariable("id") String id) throws Exception {
		Event event = playerService.getEventById(id);
		//eList.forEach((p) -> p.setTeeList(null));
		System.out.println("event ======  "+event);
		
		return new ResponseEntity<Object>(event, HttpStatus.OK);
	}
	
	
	
	@CrossOrigin("*")
	@PostMapping(path = "/webapi/admin/game/submitScore", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> submitScore(@RequestBody Event event, @AuthenticationPrincipal OidcUser principal) throws Exception {
		log.debug("input eventGroup="+event);
		checkPrincipal(principal);
		System.out.println("input submitScore="+event.getTeeList().get(0).getTeeTeamXrefList().size());
		System.out.println("input submitScore="+event);
		//System.out.println("input submitScore="+event.getTeeList().get(0).getTeeTeamXrefList().get(0).getPlayScoreList().get(0).getScore());
		playerService.submitScore(event);
		return new ResponseEntity<Object>(null, HttpStatus.OK);
	}
	
	@CrossOrigin("*")
	@DeleteMapping(path = "/webapi/admin/events/{id}", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> deleteEventsById(@PathVariable("id") String id, @AuthenticationPrincipal OidcUser principal) throws Exception {
		checkPrincipal(principal);
		
		playerService.deleteEventById(id);

		return new ResponseEntity<Object>(null, HttpStatus.OK);
	}
	
	
	
	@CrossOrigin("*")
	@GetMapping(path = "/webapi/player/last3score", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> updateLast3Score() throws Exception {
		playerService.updateLast3Score();
		return new ResponseEntity<Object>(null, HttpStatus.OK);
	}
	
	@CrossOrigin("*")
	@GetMapping(path = "/webapi/donations", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> getAllDonations() throws Exception {
		List<Donation> dList = playerService.getAllDonations();
		//eList.forEach((p) -> p.setTeeList(null));
		System.out.println("pList ======  "+dList.size());
		
		return new ResponseEntity<Object>(dList, HttpStatus.OK);
	}
	
	@CrossOrigin("*")
	@GetMapping(path = "/webapi/events/upcoming", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> getUpcomingEvent() throws Exception {
		Event event = playerService.getUpcomingEvent();
		//eList.forEach((p) -> p.setTeeList(null));
		System.out.println("upcoming event ======  "+event);
		
		return new ResponseEntity<Object>(event, HttpStatus.OK);
	}
	
	@CrossOrigin("*")
	@GetMapping(path = "/webapi/events/latest", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> getLatestEvents() throws Exception {
		List<Event> eList = playerService.getLatestEvents();
		//eList.forEach((p) -> p.setTeeList(null));
		System.out.println("pList ======  "+eList.size());
		
		return new ResponseEntity<Object>(eList, HttpStatus.OK);
	}
	
	@CrossOrigin("*")
	@GetMapping(path = "/webapi/scores/statistic", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> getScoreStatistic() throws Exception {
		List<ScoreStatistic> scoreList = null ; // playerService.getScoreStatistic();
		//eList.forEach((p) -> p.setTeeList(null));
		//System.out.println("pList ======  "+scoreList.size());
		
		return new ResponseEntity<Object>(scoreList, HttpStatus.OK);
	}	
	
	@CrossOrigin("*")
	@ResponseBody
	@PostMapping(path = "/webapi/admin/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public String uploadFile(@RequestParam("file") MultipartFile file, @RequestParam String lastModified) throws Exception {
		
		System.out.println("file ======  "+file+ ":" + lastModified);
		
		if (!(file.getOriginalFilename().toLowerCase().endsWith("jpg")||file.getOriginalFilename().toLowerCase().endsWith("jpeg"))) {
			throw new Exception("only img file are allowed.");
		}
        try {
            // Create the uploads directory if it doesn't exist
        	Calendar calendar = Calendar.getInstance();
        	calendar.setTimeInMillis(Long.parseLong(lastModified));
        	int year = calendar.get(Calendar.YEAR);
        	int month = calendar.get(Calendar.MONTH)+1;
        	String strFilePath = UPLOAD_DIR + File.separator + year + File.separator + month;
            Path uploadPath = Paths.get(strFilePath);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileNameNew = lastModified;
            // Save the file
            Path filePath = uploadPath.resolve(fileNameNew);
            System.out.println("file ======  "+filePath.toAbsolutePath().toString());
            Files.copy(file.getInputStream(), filePath);
            

            FileTime fileTime = FileTime.fromMillis(Long.parseLong(lastModified));
            Files.setAttribute(filePath, "lastModifiedTime", fileTime);
        
            

            return "pics/" + year + "/" + month + "/" + file.getOriginalFilename().toLowerCase();
        } catch (IOException e) {
            return "Failed to upload file: ";
        }
	}	
	
	
	@CrossOrigin("*")
	@PostMapping(path = "/webapi/event/onboard/{eventId}", produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> onboardEventUser(@PathVariable("eventId") String eventId, @RequestBody List<EventUser> eventUsers, @AuthenticationPrincipal OidcUser principal) throws Exception {
		checkPrincipal(principal);
		List<EventUser> eventUser = playerService.onboardEventUser(eventId, eventUsers);
		//System.out.println("pList ======  "+scoreList.size());
		
		return new ResponseEntity<Object>(eventUser, HttpStatus.OK); 
	}
	
	/*
	 * @CrossOrigin("*")
	 * 
	 * @GetMapping(path = "/webapi/event/onboard/{eventId}",
	 * produces=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<Object>
	 * onboardEventUser(@PathVariable("eventId") String eventId) throws Exception {
	 * 
	 * List<Player> players = playerService.getAllPlayersFromEventId(eventId);
	 * //System.out.println("pList ======  "+scoreList.size());
	 * 
	 * return new ResponseEntity<Object>(players, HttpStatus.OK); }
	 * 
	 * 
	 * @CrossOrigin("*")
	 * 
	 * @GetMapping(path = "/webapi/event/onboard/{eventId}",
	 * produces=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<Object>
	 * onboardEventUser(@PathVariable("eventId") String eventId) throws Exception {
	 * 
	 * List<Player> players = playerService.getAllPlayersFromEventId(eventId);
	 * 
	 * 
	 * //System.out.println("pList ======  "+scoreList.size());
	 * 
	 * return new ResponseEntity<Object>(players, HttpStatus.OK); }
	 */
	
}

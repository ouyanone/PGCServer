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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shiyuan.dao.entity.AccessToken;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.service.PlayerService;

@RestController
public class PlayerController extends BaseController {

    private final static Logger log = LoggerFactory.getLogger(PlayerController.class);

    @Autowired
    PlayerService playerService;

    String UPLOAD_DIR = "/myApp/apache-tomcat-10.1.30/photo";

    @GetMapping(path = "/webapi/players", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getAllPlayers(@AuthenticationPrincipal OidcUser principal) throws Exception {
        List<Player> pList = playerService.getAllPlayers();
        pList.forEach((p) -> System.out.println(p));
        System.out.println("pList ======  " + pList.size());
        return new ResponseEntity<Object>(pList, HttpStatus.OK);
    }

    @GetMapping(path = "/webapi/players/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getPlayerbyId(@PathVariable("id") String id) throws Exception {
        Player p = playerService.getPlayerById(id);
        return new ResponseEntity<Object>(p, HttpStatus.OK);
    }

    @PostMapping(path = "/webapi/admin/players", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Player> createPlayer(@RequestBody Player player, @AuthenticationPrincipal OidcUser principal) throws Exception {
        log.debug("input player=" + player);
        System.out.println("input player=" + player);
        checkPrincipal(principal);
        Player savedPlayer = playerService.savePlayer(player);
        return new ResponseEntity<Player>(savedPlayer, HttpStatus.OK);
    }

    @PostMapping(path = "/webapi/players/ghin", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> updatePlayerGhin(@RequestBody AccessToken token) throws Exception {
        log.debug("input token=" + token);
        System.out.println("input token=" + token);
        playerService.syncHandicap(null, token.getToken());
        return new ResponseEntity<Object>(null, HttpStatus.OK);
    }

    @GetMapping(path = "/webapi/player/last3score", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> updateLast3Score() throws Exception {
        playerService.updateLast3Score();
        return new ResponseEntity<Object>(null, HttpStatus.OK);
    }

    @ResponseBody
    @PostMapping(path = "/webapi/admin/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadFile(@RequestParam("file") MultipartFile file, @RequestParam String lastModified) throws Exception {
        System.out.println("file ======  " + file + ":" + lastModified);
        if (!(file.getOriginalFilename().toLowerCase().endsWith("jpg") || file.getOriginalFilename().toLowerCase().endsWith("jpeg"))) {
            throw new Exception("only img file are allowed.");
        }
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(Long.parseLong(lastModified));
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1;
            String strFilePath = UPLOAD_DIR + File.separator + year + File.separator + month;
            Path uploadPath = Paths.get(strFilePath);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            String fileNameNew = lastModified;
            Path filePath = uploadPath.resolve(fileNameNew);
            System.out.println("file ======  " + filePath.toAbsolutePath().toString());
            Files.copy(file.getInputStream(), filePath);
            FileTime fileTime = FileTime.fromMillis(Long.parseLong(lastModified));
            Files.setAttribute(filePath, "lastModifiedTime", fileTime);
            return "pics/" + year + "/" + month + "/" + file.getOriginalFilename().toLowerCase();
        } catch (IOException e) {
            return "Failed to upload file: ";
        }
    }
}

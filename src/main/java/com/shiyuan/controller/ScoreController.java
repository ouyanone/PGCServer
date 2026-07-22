package com.shiyuan.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shiyuan.dao.entity.PlayerGolfScore;
import com.shiyuan.dao.entity.PlayerGolfScoreEntrys;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.entity.db.ScoreStatistic;
import com.shiyuan.service.ScoreService;

import io.micrometer.common.util.StringUtils;

@RestController
public class ScoreController extends BaseController {

    @Autowired
    ScoreService scoreService;

    @GetMapping(path = "/webapi/scores/statistic", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getScoreStatistic() throws Exception {
        List<ScoreStatistic> scoreList = null; // scoreService.getScoreStatistic();
        return new ResponseEntity<Object>(scoreList, HttpStatus.OK);
    }

    @GetMapping(path = "/webapi/event/score", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getScoreForEvent(@RequestParam(required = false) String eventId) throws Exception {
        if (StringUtils.isEmpty(eventId)) {
            eventId = "1144";
        }
        Long eventLongId = Long.parseLong(eventId);
        List<PlayerScore> psList = scoreService.findPlayerScoreForEvent(eventLongId);
        return new ResponseEntity<Object>(psList, HttpStatus.OK);
    }

    @PostMapping(path = "/webapi/event/scores", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> submitEventScore(@RequestBody List<PlayerGolfScore> scores,
                                                   @RequestParam(required = false) Long eventId,
                                                   @AuthenticationPrincipal OidcUser principal) throws Exception {
        scoreService.submitPlayerScore(scores, eventId);
        return new ResponseEntity<Object>("successful", HttpStatus.OK);
    }

    @PostMapping(path = "/webapi/event/create/playerscores", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> addEventPlayer(@RequestBody List<PlayerGolfScoreEntrys> scorePlayerEntrys, @AuthenticationPrincipal OidcUser principal) throws Exception {
        scoreService.addEventPlayer(scorePlayerEntrys);
        return new ResponseEntity<Object>("successful", HttpStatus.OK);
    }
}

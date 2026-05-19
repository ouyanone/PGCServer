package com.shiyuan.service.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import com.shiyuan.dao.entity.GhinUser;
import com.shiyuan.dao.entity.Golfers;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.repository.PlayerRepository;
import com.shiyuan.dao.repository.PlayerScoreRepository;
import com.shiyuan.service.PlayerService;

@Service
public class PlayerServiceImpl implements PlayerService {

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    PlayerScoreRepository playerScoreRepository;

    @Autowired
    RestTemplate restTemplate;

    private String MY_GHIN_FOLLOWED_GOLFER_URL = "https://api2.ghin.com/api/v1/followed_golfers/11916718.json?source=GHINcom";
    private String GHIN_URL = "https://api2.ghin.com/api/v1/golfers.json?status=Active&from_ghin=true&per_page=25&page=1&golfer_id={{ghinId}}&includeLowHandicapIndex=true&source=GHINcom";

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
            ResponseEntity<GhinUser> rsp = restTemplate.exchange(
                    RequestEntity.get(new URI(MY_GHIN_FOLLOWED_GOLFER_URL)).headers(headers).build(), GhinUser.class);
            GhinUser guser = rsp.getBody();
            System.out.println("ghinuser=" + guser);
            for (int i = 0; i < guser.getGolfers().length; i++) {
                Golfers golfer = guser.getGolfers()[i];
                try {
                    Player p = playerRepository.findFirstByGhinNumber(golfer.getId());
                    if (p == null) {
                        continue;
                    }
                    p.setHandicap(Double.parseDouble(golfer.getHandicap_index_display()));
                    playerRepository.save(p);
                    System.out.println("gofer:" + p.getfName() + " " + p.getlName() + " handicap to::"
                            + (golfer.getHandicap_index_display()));
                } catch (NumberFormatException e) {
                    System.out.println("could not sync handicap for gofer:" + golfer.getFirst_name() + " "
                            + golfer.getFirst_name());
                    e.printStackTrace();
                }
            }
        } else {
            // TODO: to query one golfer's information
            org.springframework.http.HttpEntity<GhinUser> entity =
                    new org.springframework.http.HttpEntity<>(new GhinUser(), headers);
            restTemplate.exchange(GHIN_URL, org.springframework.http.HttpMethod.GET, entity, GhinUser.class);
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
    public void updateLast3Score() {
        Iterable<Player> pi = playerRepository.findAll();
        List<Player> pList = new ArrayList<Player>();
        pi.forEach(pList::add);

        for (Player player : pList) {
            List<PlayerScore> psList = playerScoreRepository.getLast3Records(player.getId());
            int numOfScore = psList.size();
            if (numOfScore > 0) {
                System.out.println("updateing last3Score for ::" + player.getfName());
                int sum = psList.stream().mapToInt(PlayerScore::getScore).sum();
                double avgScore = sum / numOfScore;
                avgScore = Math.round(avgScore * 100.0) / 100.0;
                System.out.println(player.getfName() + " updateing last3Score to ::" + avgScore);
                player.setLast3GameAvg(avgScore);
                playerRepository.save(player);
            }
        }
    }
}

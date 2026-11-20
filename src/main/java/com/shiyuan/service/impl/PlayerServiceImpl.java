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
        return getAllPlayers(false);
    }

    @Override
    public List<Player> getAllPlayers(boolean includeInactive) {
        Iterable<Player> pi = includeInactive
                ? playerRepository.findAll()
                : playerRepository.findByIsActive(true);
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
    @org.springframework.transaction.annotation.Transactional
    public Player addPlayer(com.shiyuan.controller.api.AdminApiDtos.PlayerFields f) {
        String first = requiredName(f.firstName(), "firstName");
        String last = requiredName(f.lastName(), "lastName");
        String gender = gender(f.gender());
        String ghin = blankToNull(f.ghinNumber());
        for (Player p : playerRepository.findAll()) {
            if (first.equalsIgnoreCase(trim(p.getfName())) && last.equalsIgnoreCase(trim(p.getlName()))) {
                throw com.shiyuan.service.AdminRequestException.conflict("Player " + p.getId() + " is already called "
                        + p.getfName() + " " + p.getlName() + "; nothing was created.");
            }
        }
        refuseGhinTaken(ghin, null);
        Player p = new Player();
        p.setfName(first);
        p.setlName(last);
        p.setNickName(blankToNull(f.nickName()));
        p.setChineseNickName(blankToNull(f.chineseNickName()));
        p.setGhinNumber(ghin);
        p.setGender(gender);
        if (f.handicap() != null) p.setHandicap(f.handicap());
        p.setIsActive(f.active() == null || f.active());
        return playerRepository.save(p);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public Player updatePlayer(long id, com.shiyuan.controller.api.AdminApiDtos.PlayerFields f) {
        Player p = playerRepository.findById(id).orElseThrow(() ->
                com.shiyuan.service.AdminRequestException.notFound("Player " + id + " does not exist."));
        if (f.firstName() == null && f.lastName() == null && f.nickName() == null && f.chineseNickName() == null
                && f.ghinNumber() == null && f.gender() == null && f.handicap() == null && f.active() == null) {
            throw com.shiyuan.service.AdminRequestException.badRequest("Give at least one field to change.");
        }
        if (f.firstName() != null) p.setfName(requiredName(f.firstName(), "firstName"));
        if (f.lastName() != null) p.setlName(requiredName(f.lastName(), "lastName"));
        if (f.nickName() != null) p.setNickName(blankToNull(f.nickName()));
        if (f.chineseNickName() != null) p.setChineseNickName(blankToNull(f.chineseNickName()));
        if (f.ghinNumber() != null) {
            String ghin = blankToNull(f.ghinNumber());
            refuseGhinTaken(ghin, p.getId());
            p.setGhinNumber(ghin);
        }
        if (f.gender() != null) p.setGender(gender(f.gender()));
        if (f.handicap() != null) p.setHandicap(f.handicap());
        if (f.active() != null) p.setIsActive(f.active());
        return playerRepository.save(p);
    }

    private void refuseGhinTaken(String ghin, Long self) {
        if (ghin == null) return;
        for (Player other : playerRepository.findAll()) {
            if (ghin.equalsIgnoreCase(trim(other.getGhinNumber())) && !other.getId().equals(self)) {
                throw com.shiyuan.service.AdminRequestException.conflict("GHIN number " + ghin
                        + " already belongs to player " + other.getId() + "; nothing was changed.");
            }
        }
    }

    private static String requiredName(String value, String field) {
        if (value == null || value.isBlank()) {
            throw com.shiyuan.service.AdminRequestException.badRequest("The " + field + " is required.");
        }
        return value.trim();
    }

    private static String gender(String value) {
        String g = blankToNull(value);
        if (g == null) return null;
        if (g.equalsIgnoreCase("M") || g.equalsIgnoreCase("F")) return g.toUpperCase();
        throw com.shiyuan.service.AdminRequestException.badRequest("The gender must be M or F (or left out), not \"" + g + "\".");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
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

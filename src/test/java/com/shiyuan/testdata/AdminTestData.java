package com.shiyuan.testdata;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import com.shiyuan.dao.entity.db.Course;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.EventPlayer;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.entity.db.Season;
import com.shiyuan.dao.entity.db.Tee;
import com.shiyuan.dao.entity.db.Tournament;

/**
 * Builds throwaway rows for admin tests inside the test's transaction (rolled back afterwards).
 * Seasons and courses are taken from the local database; everything else is created here.
 */
public class AdminTestData {

    private final EntityManager em;

    public AdminTestData(EntityManager em) {
        this.em = em;
    }

    public Season anySeason() {
        return em.createQuery("select s from Season s order by s.id desc", Season.class)
                .setMaxResults(1).getSingleResult();
    }

    public Course anyCourse() {
        return em.createQuery("select c from Course c order by c.id", Course.class)
                .setMaxResults(1).getSingleResult();
    }

    public Player player(String first, String last) {
        Player p = new Player();
        p.setfName(first);
        p.setlName(last);
        p.setHandicap(18.0);
        p.setPgcHandicap(18.0);
        p.setGender("M");
        p.setIsActive(true);
        em.persist(p);
        return p;
    }

    public List<Player> players(int n) {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        List<Player> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(player("T" + tag, "Player" + i));
        }
        return list;
    }

    public Tournament tournament(LocalDate start, LocalDate end) {
        Tournament t = new Tournament();
        t.setName("TEST " + UUID.randomUUID().toString().substring(0, 8));
        t.setStartDate(start);
        t.setEndDate(end);
        t.setSeason(anySeason());
        em.persist(t);
        return t;
    }

    public Event event(LocalDate date) {
        Event e = new Event();
        e.setEventName("TEST event " + UUID.randomUUID().toString().substring(0, 8));
        e.setEventDesc("");
        e.setEventStory("");
        e.setEventDate(date);
        e.setSeason(anySeason());
        e.setCourse(anyCourse());
        em.persist(e);
        return e;
    }

    public void roster(Event e, List<Player> players) {
        for (Player p : players) {
            EventPlayer ep = new EventPlayer();
            ep.setEvent(e);
            ep.setPlayer(p);
            em.persist(ep);
        }
    }

    /** A tee holding one player's score row with the given score (0 = not yet played). */
    public PlayerScore scoreRow(Event e, Player p, int score) {
        Tee tee = new Tee();
        tee.setTeeName("Tee 1");
        tee.setTeeDesc("");
        tee.setTeeStroy("");
        tee.setEvent(e);
        em.persist(tee);
        PlayerScore ps = new PlayerScore();
        ps.setPlayer(p);
        ps.setTee(tee);
        ps.setRecordStory("");
        ps.setScore(score);
        ps.setGamePoint(0);
        ps.setFlight(0);
        ps.setEntryScore(0.0);
        ps.setNetScore(0.0);
        ps.setEntryPGCHandicap(0.0);
        ps.setCurrentRoundHandicap(0.0);
        em.persist(ps);
        return ps;
    }

    public void flushAndClear() {
        em.flush();
        em.clear();
    }

    public long count(String jpql, Object param) {
        return em.createQuery(jpql, Long.class).setParameter(1, param).getSingleResult();
    }
}

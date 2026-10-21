package com.shiyuan.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import org.springframework.transaction.annotation.Transactional;

import com.shiyuan.dao.entity.PlayerGolfScore;
import com.shiyuan.dao.entity.PlayerGolfScoreEntrys;
import com.shiyuan.dao.entity.db.Course;
import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.entity.db.PlayerScore;
import com.shiyuan.dao.entity.db.Tee;
import com.shiyuan.dao.repository.EventRepository;
import com.shiyuan.dao.repository.PlayerRepository;
import com.shiyuan.dao.repository.PlayerScoreRepository;
import com.shiyuan.service.HandicapCalculator;
import com.shiyuan.service.InvalidCourseDataException;
import com.shiyuan.service.ScoreService;

@Service
public class ScoreServiceImpl implements ScoreService {

    @Autowired
    PlayerScoreRepository playerScoreRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Override
    public List<PlayerScore> findPlayerScoreForEvent(Long eventId) {
        return playerScoreRepository.getPlayerScoreForEvent(eventId);
    }

    @Override
    @Transactional
    public void submitPlayerScore(List<PlayerGolfScore> scores, Long eventId) {
        // Upload scores to the caller-selected event; fall back to the current
        // INIT event only when no event id was provided (backwards compatible).
        Long targetEventId = eventId;
        if (targetEventId == null) {
            Event currentInitEvent = eventRepository.findFirstByStatus("INIT");
            targetEventId = currentInitEvent != null ? currentInitEvent.getId() : null;
        }
        System.out.println("submitPlayerScore targetEventId::" + targetEventId);

        // Resolve and validate the course up front so a bad setup fails before anything
        // is written, rather than silently scoring the field against a default course.
        Course course = requireScorableCourse(targetEventId);

        List<PlayerScore> psEntityList = playerScoreRepository.getPlayerScoreForEvent(targetEventId);
        int i = 0;
        for (PlayerGolfScore ps : scores) {
            i++;
            String fname = "";
            String lname = "";
            String name = ps.getName();

            try {
                String[] nm = name.split(" ");
                if (nm.length > 1) {
                    fname = nm[0];
                    lname = nm[1];
                } else {
                    fname = nm[0];
                }

                System.out.println(i + " fname: " + fname + " lname: " + lname);
                for (PlayerScore psEntity : psEntityList) {
                    if (psEntity.getPlayer().getfName().equalsIgnoreCase(fname)
                            && psEntity.getPlayer().getlName().equalsIgnoreCase(lname)) {

                        if (ps.getH1() != null) psEntity.setHole1(getHoleScore(ps.getH1()));
                        if (ps.getH2() != null) psEntity.setHole2(getHoleScore(ps.getH2()));
                        if (ps.getH3() != null) psEntity.setHole3(getHoleScore(ps.getH3()));
                        if (ps.getH4() != null) psEntity.setHole4(getHoleScore(ps.getH4()));
                        if (ps.getH5() != null) psEntity.setHole5(getHoleScore(ps.getH5()));
                        if (ps.getH6() != null) psEntity.setHole6(getHoleScore(ps.getH6()));
                        if (ps.getH7() != null) psEntity.setHole7(getHoleScore(ps.getH7()));
                        if (ps.getH8() != null) psEntity.setHole8(getHoleScore(ps.getH8()));
                        if (ps.getH9() != null) psEntity.setHole9(getHoleScore(ps.getH9()));
                        if (ps.getH10() != null) psEntity.setHole10(getHoleScore(ps.getH10()));
                        if (ps.getH11() != null) psEntity.setHole11(getHoleScore(ps.getH11()));
                        if (ps.getH12() != null) psEntity.setHole12(getHoleScore(ps.getH12()));
                        if (ps.getH13() != null) psEntity.setHole13(getHoleScore(ps.getH13()));
                        if (ps.getH14() != null) psEntity.setHole14(getHoleScore(ps.getH14()));
                        if (ps.getH15() != null) psEntity.setHole15(getHoleScore(ps.getH15()));
                        if (ps.getH16() != null) psEntity.setHole16(getHoleScore(ps.getH16()));
                        if (ps.getH17() != null) psEntity.setHole17(getHoleScore(ps.getH17()));
                        if (ps.getH18() != null) psEntity.setHole18(getHoleScore(ps.getH18()));

                        if (ps.getScore() != null) psEntity.setScore(getHoleScore(ps.getScore()));

                        if (psEntity.getEntryPGCHandicap() == null) {
                            psEntity.setEntryPGCHandicap(psEntity.getPlayer().getPgcHandicap());
                        }

                        if (ps.getScore() != null) {
                            psEntity.setCurrentRoundHandicap(HandicapCalculator.scoreDifferential(
                                    psEntity.getScore(), course.getRating(), course.getSlop()));

                            // player.pgc_handicap is deliberately NOT touched here: it is a
                            // rolling mean of recent rounds, recalculated by HandicapService
                            // when the event reaches FINISHED (STEP43).
                            Double entryHandicap = psEntity.getEntryPGCHandicap();
                            if (entryHandicap != null) {
                                psEntity.setNetScore(psEntity.getScore() - entryHandicap);
                            }
                        }

                        System.out.println("save score for :: " + name);
                        playerScoreRepository.save(psEntity);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("the user with name could not be processed:: " + name);
                continue;
            }
        }
    }

    @Override
    public void addEventPlayer(List<PlayerGolfScoreEntrys> scorePlayerEntrys) {
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

                        List<PlayerScore> psList = playerScoreRepository.findByTeeId(10000L);
                        if (psList != null && psList.size() > 0) {
                            for (PlayerScore ps1 : psList) {
                                if (ps1.getPlayer().getfName().equalsIgnoreCase(fname)
                                        && ps1.getPlayer().getlName().equalsIgnoreCase(lname)) {
                                    break outerLoop;
                                }
                            }
                        }

                        playerScoreRepository.save(ps);
                    }
                }
            } catch (Exception e) {
                // continue silently as in original
            }
        }
    }

    /**
     * Returns the course the event is played on, or throws with an actionable message.
     * A round handicap is meaningless without the rating and slope actually played, so
     * an incomplete setup is refused rather than defaulted.
     */
    private Course requireScorableCourse(Long eventId) {
        if (eventId == null) {
            throw new InvalidCourseDataException(
                    "No event was selected for this score upload. Choose a started event and try again.");
        }
        Event event = eventRepository.findById(eventId.longValue());
        if (event == null) {
            throw new InvalidCourseDataException("Event " + eventId + " was not found.");
        }
        Course course = event.getCourse();
        if (course == null) {
            throw new InvalidCourseDataException("Event '" + event.getEventName()
                    + "' has no course assigned. Set the course in Game Management before uploading scores.");
        }
        if (course.getRating() == null || course.getRating() <= 0
                || course.getSlop() == null || course.getSlop() <= 0) {
            throw new InvalidCourseDataException("Course '" + course.getClubName() + " — "
                    + course.getCourseName() + "' is missing a valid rating or slope."
                    + " Update it under Plan a Game → Manage Courses.");
        }
        return course;
    }

    private Integer getHoleScore(String offSet) {
        if (StringUtils.isEmpty(offSet)) {
            return null;
        }
        return Integer.valueOf(offSet);
    }
}

package com.shiyuan.dao.repository;

import com.shiyuan.dao.entity.db.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {
    List<Tournament> findAllByOrderByStartDateDesc();
}

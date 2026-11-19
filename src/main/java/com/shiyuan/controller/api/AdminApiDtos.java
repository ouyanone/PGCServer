package com.shiyuan.controller.api;

import java.time.LocalDate;
import java.util.List;

/**
 * The request and response shapes of {@code /api/admin}. Small and explicit on purpose: the
 * caller is a tool driven by a language model, so every field it may send is named here and
 * nothing else of an entity can be written by accident.
 */
public final class AdminApiDtos {

    private AdminApiDtos() {}

    public record CreateTournament(String name, LocalDate startDate, LocalDate endDate, Long seasonId,
                                   String description) {}

    public record TournamentView(long id, String name, LocalDate startDate, LocalDate endDate,
                                 long seasonId, String seasonName) {}

    public record CreateEvent(String name, LocalDate date, Long courseId, String description) {}

    public record EventView(long id, String name, LocalDate date, long courseId, String courseName,
                            long tournamentId) {}

    public record CreateReward(Long eventId, Long playerId, String rewardName, String rewardDesc,
                               String rewardGroup, Integer displayOrder) {}

    public record RewardView(long id, long eventId, long playerId, String playerName, String rewardName) {}

    public record AddPlayers(List<Long> playerIds) {}

    public record GenerateGroups(String strategy) {}

    public record SetTee(String teeTime, String teeName) {}

    public record ErrorBody(String error) {}
}

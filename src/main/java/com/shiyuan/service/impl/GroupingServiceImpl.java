package com.shiyuan.service.impl;

import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.service.GroupingService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroupingServiceImpl implements GroupingService {

    private static final int MAX_GROUP_SIZE = 4;

    @Override
    public List<List<Player>> generateGroups(List<Player> players, Strategy strategy) {
        return switch (strategy) {
            case GHIN_BEST_TO_WORST       -> serialGroups(sortByGhin(players));
            case GHIN_BEST_TO_WORST_BY_GENDER -> byGender(players, this::sortByGhin, this::serialGroups);
            case PGC_BEST_TO_WORST        -> serialGroups(sortByPgc(players));
            case PGC_BEST_TO_WORST_BY_GENDER  -> byGender(players, this::sortByPgc, this::serialGroups);
            case GHIN_BEST_WITH_WORST     -> snakeGroups(sortByGhin(players));
            case PGC_BEST_WITH_WORST      -> snakeGroups(sortByPgc(players));
            case RANDOM                   -> serialGroups(shuffled(players));
        };
    }

    // ── Sorting helpers ──────────────────────────────────────────────────────

    private List<Player> sortByGhin(List<Player> players) {
        return players.stream()
                .sorted(Comparator.comparingDouble(Player::getHandicap))
                .collect(Collectors.toList());
    }

    private List<Player> sortByPgc(List<Player> players) {
        return players.stream()
                .sorted(Comparator.comparingDouble(p -> (p.getPgcHandicap() != null ? p.getPgcHandicap() : 99.0)))
                .collect(Collectors.toList());
    }

    private List<Player> shuffled(List<Player> players) {
        List<Player> copy = new ArrayList<>(players);
        Collections.shuffle(copy);
        return copy;
    }

    // ── Gender split ─────────────────────────────────────────────────────────

    private List<List<Player>> byGender(List<Player> players,
                                         java.util.function.Function<List<Player>, List<Player>> sorter,
                                         java.util.function.Function<List<Player>, List<List<Player>>> grouper) {
        List<Player> males   = players.stream().filter(p -> "M".equalsIgnoreCase(p.getGender())).collect(Collectors.toList());
        List<Player> females = players.stream().filter(p -> "F".equalsIgnoreCase(p.getGender())).collect(Collectors.toList());
        List<Player> unknown = players.stream().filter(p -> p.getGender() == null || (!p.getGender().equalsIgnoreCase("M") && !p.getGender().equalsIgnoreCase("F"))).collect(Collectors.toList());

        List<List<Player>> result = new ArrayList<>();
        if (!males.isEmpty())   result.addAll(grouper.apply(sorter.apply(males)));
        if (!females.isEmpty()) result.addAll(grouper.apply(sorter.apply(females)));
        if (!unknown.isEmpty()) result.addAll(grouper.apply(sorter.apply(unknown)));
        return result;
    }

    // ── Serial grouping: [1,2,3,4], [5,6,7,8] ... ───────────────────────────

    private List<List<Player>> serialGroups(List<Player> sorted) {
        int[] sizes = groupSizes(sorted.size());
        List<List<Player>> groups = new ArrayList<>();
        int idx = 0;
        for (int size : sizes) {
            groups.add(new ArrayList<>(sorted.subList(idx, idx + size)));
            idx += size;
        }
        return groups;
    }

    // ── Snake grouping: best+worst balanced ─────────────────────────────────

    private List<List<Player>> snakeGroups(List<Player> sorted) {
        int[] sizes = groupSizes(sorted.size());
        int numGroups = sizes.length;
        List<List<Player>> groups = new ArrayList<>();
        for (int i = 0; i < numGroups; i++) groups.add(new ArrayList<>());

        int playerIdx = 0;
        boolean forward = true;
        while (playerIdx < sorted.size()) {
            if (forward) {
                for (int g = 0; g < numGroups && playerIdx < sorted.size(); g++) {
                    if (groups.get(g).size() < sizes[g]) {
                        groups.get(g).add(sorted.get(playerIdx++));
                    }
                }
            } else {
                for (int g = numGroups - 1; g >= 0 && playerIdx < sorted.size(); g--) {
                    if (groups.get(g).size() < sizes[g]) {
                        groups.get(g).add(sorted.get(playerIdx++));
                    }
                }
            }
            forward = !forward;
        }
        return groups;
    }

    // ── Group size distribution ──────────────────────────────────────────────

    /**
     * Distributes N players into groups of at most 4.
     * Avoids groups of 1: if remainder == 1, converts [..., 4] into [..., 3, 2].
     *
     * Examples: 9 → [4,3,2]   10 → [4,4,2]   11 → [4,4,3]   12 → [4,4,4]   13 → [4,4,3,2]
     */
    int[] groupSizes(int n) {
        if (n <= 0) return new int[0];
        if (n <= MAX_GROUP_SIZE) return new int[]{n};

        int numFull = n / MAX_GROUP_SIZE;
        int remainder = n % MAX_GROUP_SIZE;

        if (remainder == 0) {
            int[] sizes = new int[numFull];
            Arrays.fill(sizes, MAX_GROUP_SIZE);
            return sizes;
        }

        if (remainder == 1) {
            // Replace last full group of 4 with [3, 2] to avoid a lone player
            int[] sizes = new int[numFull + 1];
            Arrays.fill(sizes, MAX_GROUP_SIZE);
            sizes[numFull - 1] = 3;
            sizes[numFull] = 2;
            return sizes;
        }

        // remainder == 2 or 3: append the remainder as a smaller group
        int[] sizes = new int[numFull + 1];
        Arrays.fill(sizes, MAX_GROUP_SIZE);
        sizes[numFull] = remainder;
        return sizes;
    }
}

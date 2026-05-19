package com.shiyuan.service;

import com.shiyuan.dao.entity.db.Player;

import java.util.List;

public interface GroupingService {

    enum Strategy {
        GHIN_BEST_TO_WORST_BY_GENDER,
        GHIN_BEST_TO_WORST,
        PGC_BEST_TO_WORST_BY_GENDER,
        PGC_BEST_TO_WORST,
        GHIN_BEST_WITH_WORST,
        PGC_BEST_WITH_WORST,
        RANDOM
    }

    /**
     * Partitions players into groups of at most 4 using the given strategy.
     * Groups of 1 are avoided — a remainder of 1 yields [..., 3, 2] instead of [..., 4, 1].
     */
    List<List<Player>> generateGroups(List<Player> players, Strategy strategy);
}

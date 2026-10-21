# STEP43 — Rolling PGC handicap from the last 5 rounds

**Date:** 2026-08-08
**Repos:** `PGCServer` (primary), `PGCUI` (one button)
**Status:** design approved, ready to implement
**Builds on:** [STEP42 — course-based round handicap](2026-08-08-course-based-handicap-design.md)

---

## 1. Problem

`pgc_handicap` is currently written inside `ScoreServiceImpl.submitPlayerScore`, the moment
scores are uploaded:

```java
psEntity.getPlayer().setPgcHandicap((currentRoundHandicap + entryHandicap) / 2);
```

Two problems with that rule:

- **It is a decaying average, not a handicap.** Each submission halves the weight of all
  prior history, so the most recent round accounts for 50% of a player's index, the one
  before it 25%, and so on. One bad round moves a player's handicap a long way.
- **It fires at upload time**, so a correction to a score, or an upload done before the
  round is confirmed, immediately rewrites the index.

## 2. The new rule

> `pgc_handicap` = the mean of the player's most recent **up to 5** round differentials
> (`player_score.current_handicap`), rounded to **2 decimals**.

- A round qualifies when its event has status **FINISHED or CLOSED**, the player has a
  gross score greater than 0, and a differential was recorded.
- Rounds count across **all seasons** — this is a rolling index, like a GHIN index, not a
  season-scoped figure like PGC Cup points.
- Fewer than 5 qualifying rounds: average however many exist. This is the common case —
  only 21 of 92 players currently have 5.
- A player with **no** qualifying rounds keeps their existing handicap; the calculation
  skips them rather than writing null or zero.

`player.pgc_handicap` is `decimal(4,2)`, so 2 decimals is exactly what the column stores.
No schema change.

### Why CLOSED counts

`CLOSED` is how an already-finished event is archived. The points calculation already
treats `FINISHED` and `CLOSED` as equivalent. Counting FINISHED only would mean archiving
an old event silently changed everyone's handicap — a surprising side effect of a filing
action.

---

## 3. Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | Trigger on **any transition into FINISHED/CLOSED**, hooked in the event save path | Admins set FINISHED through the Edit dialog (`PUT /webapi/admin/events/{id}`), not the status endpoint, so hooking only the latter would never fire |
| D2 | **Plus** an admin "Recalculate Handicaps" button | Lets an admin re-run after correcting a score, without re-saving the event |
| D3 | Submission **stops** writing `pgc_handicap` | Exactly one rule owns the column |
| D4 | Run the new calculation across existing FINISHED events **once** at cutover | Every player's handicap reflects the new rule immediately rather than drifting in |
| D5 | **No backfill** of historical `current_handicap` | Carried over from STEP42. See the caveat below. |
| D6 | Order by `event_date DESC`, tie-broken by `event.id DESC` | `player_score.score_date` is NULL on all 319 scored rows, so it cannot order anything |

### Caveat on D4 + D5 (accepted, eyes open)

The average consumes `current_handicap`, and 7 of 8 historical events computed that with
the old hardcoded 126 / 73.4 — understated by roughly 1.7–2.6 strokes. So the cutover
recalculation will move every player's handicap using values we know are low. Example:
Peter Hou 13.16 → 10.63, of which ~2 strokes is the old bug rather than the new rule.

This does not make anything *newly* wrong — the currently stored values came from the same
polluted differentials through the old running mean — and the numbers self-correct as
fresh rounds land. Backfilling `current_handicap` for past events remains a clean,
separate one-off whenever it is wanted.

---

## 4. Scope

### In scope
- The rolling average and its trigger points.
- Removing the per-submission `pgc_handicap` write.
- Admin endpoint + button to recalculate all players.
- Unit tests, positive and negative.

### Out of scope
- Backfilling historical `current_handicap` (D5).
- `entry_pgc_handicap` (snapshotted on STARTED) and `net_score = gross − entry` — unchanged.
- The USGA "best 8 of 20 differentials × 0.96" method. The club's rule is a simple mean of
  the last 5; this spec implements that, not the official index.
- `POST /webapi/event/create/playerscores` is still `permitAll` — noted in STEP42, still open.

---

## 5. Design

### 5.1 `HandicapCalculator` — extend

```java
/** Number of most-recent rounds that make up the PGC handicap. */
public static final int ROUNDS_COUNTED = 5;

/**
 * Mean of at most the first ROUNDS_COUNTED differentials, rounded to 2 decimals.
 * @param differentialsNewestFirst ordered most-recent-first; must not be empty
 */
public static double rollingHandicap(List<Double> differentialsNewestFirst);
```

Stays pure — no Spring, no JPA. Throws `IllegalArgumentException` on a null or empty list;
callers skip players with no rounds rather than passing an empty list.

### 5.2 `PlayerScoreRepository` — new query

```java
@Query("SELECT ps.currentRoundHandicap FROM PlayerScore ps "
     + "JOIN ps.tee t JOIN t.event e "
     + "WHERE ps.player.id = :playerId AND e.status IN :statuses "
     + "AND ps.score > 0 AND ps.currentRoundHandicap IS NOT NULL "
     + "ORDER BY e.eventDate DESC, e.id DESC")
List<Double> findRecentDifferentials(@Param("playerId") Long playerId,
                                     @Param("statuses") List<String> statuses,
                                     Pageable pageable);
```

`Pageable` supplies the LIMIT, so only 5 rows come back from the database.

### 5.3 `HandicapService` / `HandicapServiceImpl` — new

```java
/** Recalculate every player who has a score row in this event. */
int recalculateForEvent(Long eventId);

/** Recalculate every active player. Used by the admin button and at cutover. */
int recalculateAll();
```

Both return the number of players updated. Each delegates to one private
`recalculate(Player)` that runs the query, skips empty results, and writes the rounded
mean. Annotated `@Transactional`.

### 5.4 Trigger — `EventController`

Both status-changing endpoints (`PUT /webapi/admin/events/{id}` and
`PUT /webapi/admin/events/{id}/status`) read the previous status before saving. If the new
status is FINISHED or CLOSED and the previous status was neither, call
`handicapService.recalculateForEvent(id)` after the save.

Transitioning FINISHED → CLOSED is not a trigger: the qualifying set is unchanged.

### 5.5 Admin endpoint

`POST /webapi/admin/handicaps/recalculate` → `recalculateAll()`, returning the count.
The `/webapi/admin/**` prefix means Spring Security already requires a signed-in user.

### 5.6 `ScoreServiceImpl` — remove the write

Delete the `setPgcHandicap(...)` calls from `submitPlayerScore`. `current_handicap` and
`net_score` continue to be written exactly as STEP42 left them. The null-`entryHandicap`
branch simplifies to "no net score".

### 5.7 `PGCUI`

A "Recalculate Handicaps" button beside the existing **Calculate Points** button on the
Home page, admin-only, following that component's existing pattern (disabled while in
flight, result reported).

### 5.8 Data flow

```
Admin marks event FINISHED (Edit dialog)
  └─ PUT /webapi/admin/events/{id}
       ├─ previous status read, event saved
       └─ transition into FINISHED/CLOSED?
            └─ HandicapService.recalculateForEvent(id)
                 └─ per player in the event:
                      last ≤5 differentials (FINISHED/CLOSED, score>0, newest first)
                      └─ HandicapCalculator.rollingHandicap(...) → player.pgc_handicap

Admin clicks Recalculate Handicaps
  └─ POST /webapi/admin/handicaps/recalculate → recalculateAll()
```

---

## 6. Testing

### `HandicapCalculatorTest` (extending the STEP42 file)

| Case | Input | Expected |
|---|---|---|
| Exactly 5 rounds | 16.7, 14.9, 15.7, 12.9, 18.2 | `15.68` |
| More than 5 — only newest 5 count | above + 40.0, 41.0 appended | `15.68` |
| Fewer than 5 | 16.7, 14.9 | `15.80` |
| Single round | 16.7 | `16.70` |
| Negative differential included | 16.7, −1.9 | `7.40` |
| Rounds to 2 decimals | 10.0, 10.0, 10.1 | `10.03` |
| Empty list | `[]` | `IllegalArgumentException` |
| Null list | `null` | `IllegalArgumentException` |

### `HandicapServiceImplTest`

Positive:
- Player with 8 qualifying rounds gets the mean of the newest 5.
- Player with 2 rounds gets the mean of both.
- `recalculateForEvent` updates every player in the event and returns the count.

Negative:
- Player with no qualifying rounds keeps their previous handicap and is not counted.
- Rounds with `score = 0` and null differentials are excluded by the query (asserted via
  the arguments passed to the repository, since the filtering lives in JPQL).

### `ScoreServiceImplTest` (amended)

The STEP42 assertions that submission writes `pgc_handicap` are inverted: submission must
now leave `pgc_handicap` **unchanged**, while still writing `current_handicap` and
`net_score`.

---

## 7. Verification

1. `mvn clean test -Dspring.profiles.active=local` — all green.
2. Locally: mark a FINISHED event's status via the Edit-dialog endpoint and confirm the
   affected players' `pgc_handicap` matches a hand-computed mean of their last 5
   differentials.
3. Locally: `POST /webapi/admin/handicaps/recalculate` and confirm the count and a spot-check
   player, then confirm an anonymous call is rejected with 401.
4. Confirm a submission no longer changes `pgc_handicap`.
5. Restore any local rows touched during verification.

---

## 8. Follow-ups

- Backfill of historical `current_handicap` (D5) — the one change that would make these
  handicaps accurate on day one.
- `POST /webapi/event/create/playerscores` authentication.
- `ShiyuanApplicationTests.contextLoads` fails under the default profile because
  `application.properties` carries production DB credentials.

# STEP45 — Player score history

**Date:** 2026-10-21
**Repos:** `PGCServer` (new endpoint), `PGCUI` (new page + entry point)
**Status:** design approved, implementing

---

## 1. Intent

Members want to see their own golfing record: every round they have posted, how they scored
hole by hole, and whether they are improving. Today the app has no such view. Scores exist
only per *event* — the home page shows one game's field at a time, and the score-detail modal
is reached from Game Management. There is no way to look at one **player** across events.

This is net new. "Profile → score history" does not exist in the app today; nothing was found
under that name.

## 2. Goals

- One public page per player showing **every** round they have posted, newest first.
- Hole-by-hole detail readable as real strokes (a 5 on a par 4), with par alongside.
- A bar chart of gross scores so a trend is visible at a glance.
- Readable on a phone without sideways scrolling.

## 3. Non-goals

- Editing anything. The page is read-only for everyone.
- Comparing two players side by side.
- Any new statistic the club has not already asked for (no strokes-gained, no putting data —
  the data does not exist).

## 4. Anti-goals — what would make this a failure even if it worked

- Requiring a login. **Score information is public** (user's words); gating it defeats the point.
- A wall of numbers nobody reads. Eighteen holes × every round in one grid was explicitly
  rejected in favour of a list that opens one round at a time.
- Showing `+1` / `E` instead of strokes. The user picked the layout *because* it had room for a
  par row and real stroke counts.

## 5. Constraints and relevant facts

- **Hole scores are stored as offsets from par**, not strokes: `player_score.hole_1…hole_18`
  hold 0 for par, −1 for birdie, +2 for a double. Verified: Yan Ou's 2026-09-19 round sums to
  +14 offsets against par 72 = gross 86, matching `score`.
- **Per-hole par is available.** All 9 rows in `course` have `hole_1…hole_18` populated and each
  set sums exactly to `par_number`. Without this, real strokes would be impossible.
- **No charting library** exists in `PGCUI/package.json`.
- Ten rounds is currently the *entire* history for even the most active players, so "all rounds"
  and "last 10" are identical today and only diverge as seasons accumulate.

## 6. Decisions

| # | Decision | Whose |
|---|---|---|
| D1 | Layout: rounds listed, **one opens on tap** (mockup B) rather than all holes at once | user |
| D2 | Show **all** rounds, not a last-10 window | user |
| D3 | Public — no sign-in | user |
| D4 | **No new chart dependency**; the bars are hand-rolled CSS, as mocked | mine |
| D5 | New **public** backend endpoint returning rounds with per-hole strokes *and* par | mine |
| D6 | Server computes strokes (`par + offset`) and the nine/total subtotals, so the UI stays dumb and the arithmetic is unit-tested | mine |
| D7 | Only **FINISHED / CLOSED** events count, matching the handicap rule — an in-progress round is not history | mine |
| D8 | Entry point: a **Score History** button in the player profile dialog, which closes that dialog and opens the history dialog, to avoid stacking two dialogs | mine |
| D9 | Chart bars flex to fit; beyond ~25 rounds the chart scrolls sideways rather than shrinking to slivers | mine |

## 7. Design

### 7.1 Backend

**DTO** `com.shiyuan.dto.PlayerScoreHistory`
```
playerId, firstName, lastName, pgcHandicap, handicap
roundsPlayed, bestGross, averageGross, latestGross      // null/0 when no rounds
rounds[]  (newest first):
    eventId, eventName, eventDate, clubName, courseName, coursePar
    gross, netScore, differential, gamePoint
    holes[18]      // real strokes
    pars[18]       // course par per hole
    outStrokes, inStrokes, outPar, inPar
    eagles, birdies, parsMade
```

**Repository** — `PlayerScoreRepository`:
```java
@Query("SELECT ps FROM PlayerScore ps "
     + "WHERE ps.player.id = :playerId AND ps.tee.event.status IN :statuses AND ps.score > 0 "
     + "ORDER BY ps.tee.event.eventDate DESC, ps.tee.event.id DESC")
List<PlayerScore> findHistoryForPlayer(Long playerId, List<String> statuses);
```

**Service** `ScoreHistoryService` / `ScoreHistoryServiceImpl` — assembles the DTO: resolves each
round's course, converts offsets to strokes, computes nine subtotals and eagle/birdie/par counts,
and derives the best/average/latest summary. A missing course, or a course with no per-hole par,
degrades to offsets-as-strokes rather than failing the request (see §9).

**Controller** `PlayerController` — `GET /webapi/players/{id}/score-history`, public (not under
`/webapi/admin/**`). Unknown player id → **404**, not an empty body.

### 7.2 Frontend

New `score-history` component, opened as a wide `MatDialog`:

- **Header** — name, PGC handicap, GHIN, rounds played.
- **Tiles** — best / average / latest gross.
- **Chart** — one bar per round, oldest → newest left to right, value above each bar, best bar
  green and worst red. CSS only.
- **Round list** — date, event, course, gross, net. Clicking a row toggles it open; the most
  recent round starts open.
- **Open round** — front nine and back nine as two tables, par row above the stroke row, Out/In
  subtotals, then gross, net, differential, points and eagle/birdie/par counts. Cells tinted by
  result (birdie green, par neutral, bogey amber, double+ red).
- **Empty state** — "No rounds posted yet." when `rounds` is empty.

Entry point: a **Score History** button in `edit-player.component.html`'s dialog actions, shown
to everyone (not gated on `isLoggedIn`). It closes the profile dialog and opens this one.

## 8. Testing

**`ScoreHistoryServiceImplTest`** (positive):
- Offsets become real strokes: par 4 with offset +1 → 5; offset −1 → 3.
- Out/In subtotals and their pars are summed correctly.
- Eagles, birdies and pars counted from offsets (≤ −2, −1, 0).
- Summary: best = lowest gross, average rounded sensibly, latest = first round.
- Rounds come back newest first.

(negative):
- Player with no qualifying rounds → empty `rounds`, summary fields null/zero, no exception.
- Unknown player id → `PlayerNotFoundException` → 404.
- A round whose event has no course → falls back to offsets, request still succeeds.
- Rounds with `score = 0` and non-FINISHED events are excluded (asserted via the repository
  arguments, since the filtering is JPQL).

**Real-data check:** the JPQL ordering and the offset→stroke conversion are verified against the
production copy in the local database using a temporary read-only test, then deleted — mocks
cannot prove either.

## 9. Error handling

| Condition | Behaviour |
|---|---|
| Unknown player id | 404 with a message |
| Player exists, no rounds | 200 with empty `rounds` and an empty-state message in the UI |
| Round's event has no course | Round still listed; strokes fall back to the stored offsets and `coursePar` is null so the UI hides the par row for that round |
| Course missing per-hole par | Same fallback as above |

The fallbacks are deliberate: one badly-configured course should not blank out a member's whole
history.

## 10. Verification

1. `mvn clean test -Dspring.profiles.active=local` — all green.
2. Temporary real-data test proving order and stroke conversion against actual rounds.
3. `npm run build` — exit 0, no new warnings.
4. Spot-check the endpoint against a real player and reconcile by hand: Yan Ou's 2026-09-19
   round must come back gross 86, Out 46, In 40, 2 birdies.

## 11. Follow-ups, not in this change

- Still open from earlier sessions: `generateGroups` deletes scores before validating the roster;
  no TLS renewal automation; STEP43 handicap cutover press; nothing committed to git.

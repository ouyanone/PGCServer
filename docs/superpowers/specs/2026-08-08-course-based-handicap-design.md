# STEP42 — Course-based round handicap

**Date:** 2026-08-08
**Repos:** `PGCServer` (primary), `PGCUI` (one small change)
**Status:** design approved, ready for implementation planning

---

## 1. Problem

`ScoreServiceImpl.submitPlayerScore` computes each player's round handicap with
hardcoded course constants:

```java
double SLOPE_RATING = 126.0;
double COURSE_RATING = 73.4;
double rt = 113.0 / SLOPE_RATING;
Double currentRoundHandicap = (Double.valueOf(ps.getScore()) - COURSE_RATING) * rt;
```

This is the USGA Score Differential formula — correct in shape, but frozen to one
course. 126 / 73.4 is **Charleston Springs South** (course id 5). Every event played
anywhere else is scored against the wrong course.

The error is not cosmetic. `pgc_handicap` is updated as
`(currentRoundHandicap + entryPGCHandicap) / 2` on every submission, so each wrong
differential feeds forward into the player's running PGC handicap, which then drives
net scores and the grouping strategies on the Plan a Game page.

### Measured impact (production, 2026-08-08)

Stored `player_score.current_handicap` vs. the same rounds recomputed against the
event's real course:

| Event | Slope / Rating | Players | Stored avg | Correct avg | Error |
|---|---|---|---|---|---|
| 2026 PGC Crystal Springs Championship 第一轮 | 133 / 71.6 | 16 | 12.70 | 13.56 | +0.86 |
| 2026 PGC 赛季揭幕战第二轮 | 134 / 69.7 | 38 | 15.45 | 17.65 | **+2.20** |
| 2026 PGC 赛季揭幕战第一轮 | 134 / 70.2 | 40 | 17.47 | 19.12 | +1.66 |
| 2025 The PGC Cup | 134 / 70.2 | 40 | 14.70 | 16.40 | +1.82 |
| 2025 PGC Championship | 134 / 69.7 | 46 | 17.40 | 19.48 | **+2.08** |
| 2025 PGC Classic | 127 / 70.4 | 46 | 13.19 | 15.76 | **+2.57** |
| 2025 PGC Open *(the hardcoded course)* | 126 / 73.4 | 52 | 15.20 | 15.75 | +0.55 |
| 2025 PGC Masters Invitational | 135 / 72.8 | 41 | 16.11 | 16.03 | −0.08 |

Handicaps are systematically **understated by roughly 1.7–2.6 strokes** on seven of
eight events.

### Data readiness

No data cleanup is needed first. All 7 rows in `course` have both `slop` and `rating`
populated, and 9 of 10 events have a course assigned — the only exception is
`PlaceHolder Event` (id 1), which is not a real game.

---

## 2. Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | **Reject** the submission when course data is missing | A silent fallback is exactly how this bug survived unnoticed. Nothing is saved; the admin sets the course and re-submits. |
| D2 | **Fix forward only** — no historical backfill | Smallest safe change. Past `pgc_handicap` values stay slightly low, but every future round is correct. Backfill remains available as a separate, deliberate decision. |
| D3 | **Round the differential to 1 decimal** | Matches the USGA definition of a Score Differential. |
| D4 | Extract a `HandicapCalculator` rather than editing in place | The formula is the one part worth testing precisely and the part most likely to change again. |
| D5 | Fix the latent NPE on null `entryPGCHandicap` in the same pass | It sits on the exact lines being edited and currently causes a player to be skipped silently. |

---

## 3. Scope

### In scope
- Replace the hardcoded rating/slope with the event course's values.
- Validate course data up front; reject with an actionable message.
- Surface success and failure in the Upload Score UI.
- Unit tests, positive and negative.

### Out of scope (deliberately)
- **Historical backfill** of `current_handicap`, `net_score`, `pgc_handicap`, or points (D2).
- The `pgc_handicap = (differential + entry) / 2` averaging rule — unchanged.
- 9-hole rounds and PCC (Playing Conditions Calculation) adjustment — not currently
  modelled, not added here.
- **Security of the endpoint.** `/webapi/event/scores` is `permitAll` — only
  `/webapi/admin/**` is authenticated (`SecurityConfiguration` line 45), and
  `submitEventScore` never calls `checkPrincipal`. Any unauthenticated caller can post
  scores to a STARTED event. Real issue; tracked separately so this change stays
  reviewable.

---

## 4. Design

### 4.1 `HandicapCalculator` — new, `com.shiyuan.service`

Pure; no Spring, no JPA, no repository access.

```java
public final class HandicapCalculator {

    private static final double STANDARD_SLOPE = 113.0;

    /** USGA Score Differential, rounded to the nearest tenth. */
    public static double scoreDifferential(int grossScore, double courseRating, int slope);
}
```

- Formula: `(grossScore - courseRating) * STANDARD_SLOPE / slope`, rounded to 1 decimal
  using `BigDecimal.setScale(1, RoundingMode.HALF_UP)` — stated explicitly so the
  rounding tests are unambiguous.
- Utility class: `final`, with a private constructor.
- Throws `IllegalArgumentException` when `slope <= 0`.
- No fallback values inside. The caller guarantees valid inputs — that is what makes
  this unit testable and keeps the "reject, don't guess" decision in one place.
- A negative result is legitimate (gross below course rating) and is returned as-is.

### 4.2 `InvalidCourseDataException` — new, `com.shiyuan.service`

Extends `RuntimeException`. Carries a human-readable, actionable message intended for
display to the admin.

### 4.3 `ScoreServiceImpl.submitPlayerScore(List<PlayerGolfScore>, Long eventId)`

Changes, in order:

1. Annotate the method `@Transactional` (it is not today), so a failure part-way
   through the player loop rolls back rather than leaving half the field scored.
2. After resolving `targetEventId`, load the `Event` and its `Course` **once, before
   the loop**.
3. Validate and fail fast, before any write:
   - event exists
   - `event.getCourse() != null`
   - `course.getRating() != null && > 0`
   - `course.getSlop() != null && > 0`

   Any failure throws `InvalidCourseDataException`.
4. Inside the loop, replace the hardcoded block with:
   ```java
   double differential = HandicapCalculator.scoreDifferential(
           psEntity.getScore(), course.getRating(), course.getSlop());
   psEntity.setCurrentRoundHandicap(differential);
   ```
5. Keep the existing `entryPGCHandicap` back-fill from the player, then guard the
   averaging step (D5). When `entryPGCHandicap` is still null — a player with no
   `pgc_handicap` yet, i.e. their first scored round — the current code throws an NPE
   on unboxing, which the catch-all swallows and the player is skipped entirely. New
   behavior in that case:
   - `current_handicap` = the differential (as for everyone else),
   - `player.pgc_handicap` = the differential alone, rather than an average with null,
   - `net_score` remains unset, preserving the existing
     `if (entryPGCHandicap != null)` guard — there is no handicap to subtract.

The existing behavior of skipping rows whose name does not match a player on the
event roster is **preserved unchanged**.

### 4.4 `ScoreController`

Add an `@ExceptionHandler(InvalidCourseDataException.class)` returning **HTTP 400**
with the exception message as the response body.

### 4.5 `PGCUI` — `upload-score.component.ts`

`onSubmit` currently calls `.subscribe(item => console.log(...))`: no success feedback
and no error handling, so a 400 would be invisible. Change to
`subscribe({ next, error })` — confirm success, and on error display the message
returned by the backend.

### 4.6 Data flow

```
Upload Score UI
  └─ POST /webapi/event/scores?eventId=N
       └─ ScoreController.submitEventScore
            └─ ScoreServiceImpl.submitPlayerScore   @Transactional
                 ├─ load Event → Course
                 ├─ validate course  ──✗──> InvalidCourseDataException → 400 + message
                 └─ per matched player:
                      HandicapCalculator.scoreDifferential(gross, rating, slope)
                      → current_handicap, net_score, player.pgc_handicap
```

---

## 5. Error messages

| Condition | Message |
|---|---|
| Event not found | `Event <id> was not found.` |
| No course on event | `Event '<name>' has no course assigned. Set the course in Game Management before uploading scores.` |
| Rating or slope missing/invalid | `Course '<club> — <course>' is missing a valid rating or slope. Update it under Plan a Game → Manage Courses.` |

---

## 6. Testing

### `HandicapCalculatorTest`

| Case | Input | Expected |
|---|---|---|
| Forsgate Banks | gross 90, rating 70.2, slope 134 | `16.7` |
| Charleston Springs South | gross 90, rating 73.4, slope 126 | `14.9` |
| Gross below course rating | gross 68, rating 70.2, slope 134 | negative value, returned as-is |
| Rounds up | gross 88, rating 70.4, slope 127 (raw 15.6598…) | `15.7` |
| Rounds down | gross 85, rating 69.7, slope 134 (raw 12.9022…) | `12.9` |
| Invalid slope | slope 0 | `IllegalArgumentException` |

The first two cases together demonstrate the fix: the old constants now produce the
right answer only for their own course.

### `ScoreServiceImplTest`

Positive:
- Valid event + course writes the expected `current_handicap` for a matched player.
- `net_score` and `player.pgc_handicap` still update as before.

Negative:
- Event with no course throws `InvalidCourseDataException`; **no** `player_score` row
  is modified.
- Course with null rating throws; nothing written.
- Course with null or zero slope throws; nothing written.
- Uploaded row whose name matches no roster player is skipped, and the remaining rows
  still save.
- Player with a null `pgc_handicap` no longer throws: `current_handicap` and
  `player.pgc_handicap` both hold the differential, and `net_score` stays null.

---

## 7. Verification

1. `mvn clean test` — all tests green.
2. Run locally against the local MySQL (`-Dspring-boot.run.profiles=local`); upload the
   sample export to a STARTED event whose course is Forsgate Banks and confirm
   `current_handicap` matches the hand-computed differential.
3. Negative check: clear the course on a test event, attempt an upload, confirm a 400
   and that the UI shows the message.
4. Confirm no `player_score` rows changed for the failed attempt.

---

## 8. Follow-ups (not in this change)

- **Endpoint authentication** for `/webapi/event/scores` — see §3.
- **Historical backfill** — if wanted later, recomputing `current_handicap` alone is
  low risk; rebuilding the `pgc_handicap` chain means replaying events in date order
  and would retroactively change published net scores and standings. Take a database
  backup first.
- The `pgc_handicap` averaging rule itself is unusual (a simple two-value mean rather
  than a rolling average of recent differentials). Worth a separate conversation.

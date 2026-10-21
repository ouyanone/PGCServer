# PGCServer Implementation Progress

## Session: 2026-05-08

### STEP 1 — Clone the project locally ✅
- Identified correct repo name: `PGCServer` (plan had typo `PGCSever`)
- Cloned from `https://github.com/ouyanone/PGCServer`
- Local path: `/Users/ouyan/Documents/pgc-ai/PGCServer`
- Confirmed access: 5 commits visible, project structure intact (pom.xml, src, etc.)

### STEP 2 — Create feature branch ✅
- Created branch `feature-1` from `main`
- Currently checked out on `feature-1`

### STEP 3 — Connect to MySQL and read data ✅

- DB: `localhost:3306`, database `pgc`
- Working credentials: username `ouyan` / password `Test@1234` (user `ouyanone` from plan does not have access)
- `t_user` table does not exist — used existing `player` table instead (user's choice)
- Successfully queried 20 rows from `player` table; confirmed columns: id, first_name, last_name, ghin_number, phone, email, nick_name, handicap, club_id, etc.

### STEP 4 — Understand the app and data model ✅


#### App Summary
PGC is a golf club management web app. Angular frontend, Spring Boot backend, MySQL database. It organizes seasonal golf tournaments for a Chinese-American golf community.

#### Tables & Purpose

| Table | Rows | Purpose |
|---|---|---|
| `season` | 3 | Yearly seasons (2024, PGC 2025, 2026) |
| `club` | 2 | Golf clubs/venues (e.g. Forsgate Country Club) |
| `course` | 5 | Courses at clubs — par, slope, rating, per-hole par values |
| `event` | 8 | Tournament events — linked to a season, course, and host player |
| `tee` | 54 | Tee time slots per event (players grouped into tee times) |
| `player` | 121 | Members — name, GHIN#, handicap, PGC handicap, flight (A/B), bio |
| `player_score` | 266 | Hole-by-hole scores per player per event — gross/net score, game points |
| `event_player` | 0 | M:M join between events and players (currently unused) |
| `reward` | 44 | Post-event awards (closest to pin, longest drive, etc.) per event+player |
| `donation` | 0 | Player donations (empty, structure in place) |

#### Key Relationships
- `season` → `event` (one season has many events)
- `course` → `event` (each event is played on one course)
- `event` → `tee` (each event has multiple tee time groups)
- `player` → `player_score` (each player has many score records)
- `player_score` → `tee` (score is recorded under a tee group)
- `event` + `player` → `reward` (winners receive rewards per event)
- `player` → `donation` (members can make donations)

### STEP 5 — Understand the code structure ✅

#### Application Layers

```
HTTP Request
    ↓
MainController  (/com/shiyuan/controller/)
    ↓
PlayerService   (/com/shiyuan/service/)        ← interface
    ↓
PlayerServiceImpl (/com/shiyuan/service/impl/) ← implementation
    ├─→ 9 Repositories (/com/shiyuan/dao/repository/)
    └─→ RestTemplate   (external GHIN handicap API)
    ↓
JPA Entities    (/com/shiyuan/dao/entity/db/)
    ↓
MySQL Database
```

#### Layer Details

**Controller** — `MainController.java` (single controller, ~25 endpoints)
- Public endpoints under `/webapi/`
- Admin-protected endpoints under `/webapi/admin/` (requires OAuth2 + profile="admin")
- Delegates all logic to `PlayerService`

**Service** — `PlayerService` interface + `PlayerServiceImpl`
- All business logic lives here: handicap sync, score calculation, event registration
- Calls external GHIN API (`api2.ghin.com`) to sync player handicap indexes
- Handicap formula: `(score - 73.4) * (113 / 126)`
- Net score: `gross - pgcHandicap`

**Repositories** — 9 Spring Data `CrudRepository` interfaces
- PlayerRepository, EventRepository, PlayerScoreRepository, CourseRepository,
  SeasonRepository, TeamRepository, TeeRepository, RewardRepository, DonationRepository
- Custom JPQL queries for last-3-score average, event scores, rewards by event

**Entities (DB)** — 10 JPA-mapped classes
- Event owns Tees (cascade all, eager fetch)
- Tee owns PlayerScores (cascade all, eager fetch, orphan removal)
- Player, Course, Season, Reward, Donation, Team

**DTOs** — 7 plain POJOs (no @Entity)
- `PlayerGolfScore` — scorecard input (hole-by-hole)
- `PlayerGolfScoreEntrys` — event registration input
- `EventGroup` / `TeeObject` — event creation with grouped tee times
- `GhinUser` / `Golfers` — GHIN API response mapping
- `AccessToken` — bearer token wrapper

**Security** — `SecurityConfiguration.java`
- Amazon Cognito OAuth2 login
- CORS allowed for `localhost:4200` (Angular dev) and Cognito
- `/webapi/admin/**` requires authentication; all else is public

#### Notable Issues Found
- Event ID `1144` is hardcoded in two endpoints
- `createEvent()` and `onboardEventUser()` are unimplemented stubs (return null)
- `getScoreStatistic()` endpoint returns null
- No `@Transactional` annotations on service methods
- Minimal error handling (bare try-catch blocks)

### STEP 6 — Refactor controller into domain-based controllers ✅

Split `MainController.java` (single file, ~25 endpoints) into 5 focused controllers + 1 base class. No business logic changed.

| File | Endpoints |
|---|---|
| `BaseController.java` | Shared `checkPrincipal()` helper |
| `PlayerController.java` | GET/POST players, GHIN sync, photo upload, last-3-score |
| `EventController.java` | GET/POST/DELETE events, grouping, score submit, onboarding |
| `ScoreController.java` | Score statistic, get/submit event scores, add event players |
| `RewardController.java` | GET rewards for event |
| `DonationController.java` | GET donations |

Build verified: `mvn compile` passes with no errors.

### STEP 7 — Refactor service into domain-based services ✅

Split the monolithic `PlayerService` interface + `PlayerServiceImpl` into 5 focused services. Controllers updated to inject the appropriate service. No business logic changed.

| Interface | Implementation | Responsibilities |
|---|---|---|
| `PlayerService` | `PlayerServiceImpl` | getAllPlayers, syncHandicap (GHIN API), savePlayer, getPlayerById, updateLast3Score |
| `EventService` | `EventServiceImpl` | createEvent, getAllEvents, getEventById, submitScore, deleteEvent, getUpcoming, getLatest, onboard |
| `ScoreService` | `ScoreServiceImpl` | findPlayerScoreForEvent, submitPlayerScore, addEventPlayer |
| `RewardService` | `RewardServiceImpl` | findRewardForEvent |
| `DonationService` | `DonationServiceImpl` | getAllDonations |

Build verified: `mvn compile` — BUILD SUCCESS.

### STEP 8 — Full CRUD APIs for Donation and Reward ✅

#### Donation API — `/webapi/donations`
| Method | Path | Description |
|---|---|---|
| GET | `/webapi/donations` | List all donations (newest first) |
| GET | `/webapi/donations/{id}` | Get donation by id — 404 if not found |
| POST | `/webapi/donations` | Create new donation — returns 201 |
| PUT | `/webapi/donations/{id}` | Update donation — 404 if not found |
| DELETE | `/webapi/donations/{id}` | Delete donation — 204 on success, 404 if not found |

#### Reward API — `/webapi/rewards`
| Method | Path | Description |
|---|---|---|
| GET | `/webapi/rewards?eventId={id}` | List rewards for an event (sorted by displayOrder) |
| GET | `/webapi/rewards/{id}` | Get reward by id — 404 if not found |
| POST | `/webapi/rewards` | Create new reward — returns 201 |
| PUT | `/webapi/rewards/{id}` | Update reward — 404 if not found |
| DELETE | `/webapi/rewards/{id}` | Delete reward — 204 on success, 404 if not found |

Reward create/update request body:
```json
{
  "rewardName": "最近洞奖",
  "rewardDesc": "Closest to pin",
  "rewardStory": "...",
  "displayOrder": 1,
  "event": { "id": 1144 },
  "player": { "id": 1000 }
}
```

Also fixed: added `@Repository` to `RewardRepository` and switched its `@Query` import from `spring-data-jdbc` to `spring-data-jpa`.
Build verified: `mvn compile` — BUILD SUCCESS.

### STEP 9 — Clone and understand Angular UI ✅

**Repo**: `https://github.com/ouyanone/PGCUI` → cloned to `/Users/ouyan/Documents/pgc-ai/PGCUI`

#### Tech Stack
- Angular 16, Angular Material 16, AG Grid 31 (enterprise)
- Single-page application, component-based routing

#### App Structure

```
src/app/
├── app.module.ts              — root module (declares all 19 components)
├── app-routing.module.ts      — route config (19 routes)
├── services/
│   ├── player.service.ts      — single service, all HTTP calls
│   └── api/models/            — TypeScript interfaces matching backend entities
│       ├── player-representation.ts
│       ├── event-representation.ts
│       ├── tee.ts
│       ├── playerscore-representation.ts
│       ├── donation-representation.ts
│       └── reward-representation.ts
└── component/                 — 19 feature components
```

#### Routes → Components

| Route | Component | Purpose |
|---|---|---|
| `/` | `HomeComponent` | Dashboard — ongoing event, rewards, scores |
| `/player` | `PlayerComponent` | Player list (AG Grid), add/edit via dialog |
| `/editplayer` | `EditPlayerComponent` | Edit player dialog |
| `/gamerecord` | `GamerecordComponent` | Event list (AG Grid), opens gameinput dialog |
| `/gameplan` | `GameplanComponent` | Event planning / tee grouping |
| `/donation` | `DonationComponent` | Donation list (AG Grid) |
| `/gamerule` | `GameruleComponent` | Game rules page |
| `/statistics` | `StatisticsComponent` | Score statistics (backend returns null) |
| `/photos` | `PhotosComponent` | Photo gallery |
| `/uploadScore` | `UploadScoreComponent` | Upload CSV score data |
| `/ghinsync` | `GhinSyncComponent` | GHIN handicap sync dialog |
| `/aboutus` | `AboutusComponent` | About page |

#### Service API Calls (PlayerService)

All HTTP calls go through a single `PlayerService`. Base URL:
- Dev (`port 4200`): `http://localhost:8080/`
- Prod: relative to `window.location.host`

#### ⚠️ Breaking Change from STEP 8
The reward endpoint was moved from `/webapi/event/reward` to `/webapi/rewards` in STEP 8.
The Angular `PlayerService.getLatestRewards()` still calls the old path and will need to be updated.

### STEP 10 — Create `improved-UI` branch ✅
- Created branch `improved-UI` from `main` in PGCUI repo
- Local path: `/Users/ouyan/Documents/pgc-ai/PGCUI`

### STEP 11 — Fix all UI-backend issues ✅

Full audit of all Angular components against current backend APIs. Found and fixed 4 issues:

| # | File | Issue | Fix |
|---|---|---|---|
| 1 | `player.service.ts` | `getLatestRewards()` called old `/webapi/event/reward` (broken by STEP 8 rename) | Updated to `/webapi/rewards` |
| 2 | `playerscore-representation.ts` | Missing `entryScore` and `entryPGCHandicap` fields used by gameinput view | Added both fields to interface |
| 3 | `gameinput.component.html` | Referenced `teeTeamXrefList` (old dead data model) — event detail view was broken | Rewrote template to use `playerScoreList` directly |
| 4 | `gameinput.component.html` | Submit/Delete buttons checked `status == 'OPEN'` but events use `INIT`/`CLOSED`/`FINISHED` — buttons always hidden | Updated condition to `status === 'OPEN' or INIT` |

Build verified: `ng build` — clean, no errors.

### STEP 12 — Run app locally and fix startup bugs ✅

Started both servers locally. Fixed 3 bugs blocking startup:

| # | File | Bug | Fix |
|---|---|---|---|
| 1 | `RewardRepository.java` | JPQL typo: `SELECT o FROM Reward r` — `o` undefined | Changed to `SELECT r FROM Reward r` |
| 2 | `EventServiceImpl.java` | Dead loop calling `event.getCourse().getCourseName()` on potentially-null course → NPE at startup | Removed the dead loop bodies in `getAllEvents()` and `getLatestEvents()` |
| 3 | `Donation.java` | Entity had fields (`donor`, `cash`, `worth`, etc.) that don't exist in the actual DB table | Rewrote entity to match actual schema: `id`, `donationName`, `donationDesc`, `donationDate`, `player` |

Both servers confirmed running: Spring Boot on `:8080`, Angular dev server on `:4200`.

### STEP 13 — Modernize UI with responsive design ✅

Applied full UI overhaul across the Angular app:

- **Responsive navigation**: Replaced static sidebar with `BreakpointObserver`-driven `mat-drawer` — side mode on desktop, overlay on mobile
- **AG Grid heights**: Replaced all hardcoded pixel heights (`900px`, `1400px`) with `domLayout="autoHeight"` across home, player, gamerecord, donation templates
- **Home page**: Full rewrite — hero section with overlay, champion cards, CSS classes replacing inline styles
- **Color scheme**: Applied golf-inspired design tokens — deep green/gold theme
- Build verified: `ng build` — clean.

### STEP 14 — Professional blue/green color theme ✅

Replaced the STEP 13 green/gold theme with a professional blue/green palette:
- Deep navy toolbar (`#1e3a5f`), green accents (`#2e7d32`), dark slate sidebar
- Design tokens in `styles.css` for consistency
- Build verified: `ng build` — clean.

### STEP 15 — Lighter color theme ✅

User found STEP 14 too dark. Applied lighter palette:

| Token | Value | Use |
|---|---|---|
| `--pgc-blue` | `#1976d2` | Toolbar (medium blue) |
| `--pgc-green` | `#388e3c` | Accents / section underlines |
| `--pgc-bg` | `#f4f6f8` | Page background |
| `--pgc-surface` | `#ffffff` | Cards / sidebar |

Sidebar changed from dark slate to white with `border-right: 1px solid #e2e8f0`. Build verified: clean.

### STEP 16 — Donation CRUD UI ✅

Added full Create/Read/Update/Delete UI for donations on the `/donation` page.

**New files:**
- `donation-dialog.component.ts` / `.html` — Add/Edit dialog (Donation Name, Description, Date, Player dropdown)

**Modified files:**
- `donation.component.ts` — Full rewrite: AG Grid with Edit/Delete cell buttons, `MatDialog` integration, `loadDonations()` refresh cycle
- `donation.component.html` — Page header with "Add Donation" button, grid with `(cellClicked)` handler
- `donation.component.css` — Edit/Delete button styles via `::ng-deep`
- `donation-representation.ts` — Changed from class to interface; added `player` field
- `player.service.ts` — Added `createDonation()`, `updateDonation()`, `deleteDonation()`
- `app.module.ts` — Registered `DonationDialogComponent`
- `styles.css` — Added `.donation-form` and `.full-width` dialog layout styles

Build verified: `ng build` — clean.

### STEP 17 — Add `amount` field to donation ✅

User added `amount DECIMAL(10,2)` column to the `donation` table in MySQL.

**Backend:**
- `Donation.java` — Added `@Column(name="amount") private BigDecimal amount` with getter/setter; added `import java.math.BigDecimal`
- `DonationServiceImpl.java` — Added `resolvePlayer()` helper to fix 500 error: frontend sends `{ player: { id: 1 } }` which Jackson deserializes as an unmanaged POJO; `resolvePlayer()` does a proper `findById()` lookup before saving
- `Player.java` — Added `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` to fix 500 on `GET /webapi/donations`: Hibernate lazy proxy for `Player` was not serializable by Jackson

**Frontend:**
- `donation-representation.ts` — Added `amount?: number`
- `donation-dialog.component.ts` — Added `amount` to `FormGroup` and save result
- `donation-dialog.component.html` — Added `Amount ($)` number input field
- `donation.component.ts` — Added `Amount ($)` column to AG Grid column defs

Build verified: `ng build` — clean. API verified: `GET /webapi/donations` returns 200 with full data.

**Other fixes in this session:**
- Replaced native `confirm()` delete popup with a centered `ConfirmDeleteDialogComponent` (Material dialog with red icon, item name, Cancel/Delete buttons)
- Updated donation page subtitle from "Manage $10 club donation records" to "Manage PGC Club donation records"

### STEP 18 — Event CRUD UI (Game Management) ✅

Added full Create/Read/Update/Delete UI for events. Renamed menu item "Game Records" → "Game Management".

**New backend endpoints:**

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/webapi/courses` | Public | List all courses |
| GET | `/webapi/seasons` | Public | List all seasons |
| POST | `/webapi/admin/events` | Required | Create new event |
| PUT | `/webapi/admin/events/{id}` | Required | Update event |

- Added `EventService.saveEvent(Event)` — resolves Course and Season FKs via `findById()` before saving
- `EventController` updated with `CourseRepository` and `SeasonRepository` injected

**New frontend files:**
- `event-dialog.component.ts` / `.html` — Add/Edit dialog with fields: Event Name, Description, Date, Status (INIT/NOTSTART/FINISHED/CLOSED), Course dropdown, Season dropdown
- `course-representation.ts` / `season-representation.ts` — New model interfaces

**Modified frontend files:**
- `event-representation.ts` — Updated to class with `status`, `eventStory`, `player` fields; `course`/`season` typed as `any` for backward compatibility with `gameplan` component
- `gamerecord.component.ts` — Full CRUD rewrite with AG Grid (ID, Name, Date, Status, Course, Season, Actions columns), `ConfirmDeleteDialogComponent` for delete
- `gamerecord.component.html` — Page header + "Add Event" button + grid with `(cellClicked)` handler
- `gamerecord.component.css` — Replaced old AG Grid overrides with page layout + Edit/Delete button styles
- `menubar.component.html` — "Game Records" → "Game Management"
- `player.service.ts` — Added `getCourses()`, `getSeasons()`, `createEvent()`, `updateEvent()`, `deleteEvent()`; removed duplicate `deleteEvent`
- `app.module.ts` — Registered `EventDialogComponent`

Build verified: `ng build` — clean, no errors.

### STEP 19 — Player profile page improvements ✅

**Changes:**
- **Row click**: Replaced column-level `onCellClicked` on the ID cell with grid-level `(rowClicked)` — clicking any cell in a row now opens the player profile dialog. Added `cursor: pointer` row style so rows look clickable.
- **Columns**: Trimmed to 5 fields — First Name, Last Name, PGC Handicap, Handicap, PGC Member (boolean rendered as Yes/No via `valueFormatter`)
- **Active only**: Already handled by backend (`findByIsActive(true)` in `PlayerServiceImpl`) — no change needed
- **Page header**: Added consistent page title/subtitle layout matching donation and game management pages

**Modified files:**
- `player.component.ts` — Updated `colDefs`, replaced `CellClickedEvent` import with `RowClickedEvent`, added `onRowClicked()`, updated `openEditPlayer()` to accept plain ID
- `player.component.html` — Added page header, wired `(rowClicked)` and `[rowStyle]` on grid
- `player.component.css` — Replaced old AG Grid overrides with page layout styles

Build verified: `ng build` — clean, no errors.

### STEP 20 — Player profile detail page redesign ✅

Rewrote `EditPlayerComponent` with a modern, clean layout showing all player fields.

**Design changes:**
- Removed the broken photo card; replaced with a gradient profile header (blue→green) containing an initials avatar circle, player name, Chinese nick name, and status/membership badges
- Form fields organized into 4 labeled sections: Basic Information, Contact, Golf Information, Membership
- 2-column grid layout for all form fields (instead of the previous 25%-width scattered inputs)
- `pgc2025` and `isActive` converted from plain text inputs to `mat-slide-toggle` for intuitive boolean editing
- `desc` field shown as a full-width textarea in a Bio section
- Dialog width changed from `60% / 800px` to `680px / 90vh max`

**All fields now shown:**
id (readonly), fName, lName, nickName, chineseNickName, phone, email, ghinNumber (readonly), handicap, pgcHandicap, last3GameAvg (readonly), clubId, clubName, level, pgc2025 (toggle), isActive (toggle), desc

**Modified files:**
- `edit-player.component.ts` — Simplified, added `initials` getter, added `level` field, removed dead code
- `edit-player.component.html` — Full rewrite with profile header, sectioned 2-column grid layout
- `edit-player.component.css` — Full rewrite with gradient header, avatar circle, badge styles, form grid
- `player-representation.ts` — Added missing fields: `isActive`, `desc`, `icon`; made `last3GameAvg` optional
- `material-module.ts` — Added `MatSlideToggleModule`
- `player.component.ts` — Dialog width updated to `680px`
- `gameplan.component.ts` — Fixed sort crash after `last3GameAvg` became optional

Build verified: `ng build` — clean, no errors.

### STEP 21 — Player profile dialog: read-only for guests, editable for logged-in users ✅

**Backend:**
- New `AuthController.java` — `GET /webapi/auth/status` (public endpoint)
  - Unauthenticated: `{ "loggedIn": false }`
  - Authenticated (Cognito session): `{ "loggedIn": true, "name": "..." }`
- Verified: `curl http://localhost:8080/webapi/auth/status` → `{"loggedIn":false}`

**Frontend:**
- New `auth.service.ts` — calls `/webapi/auth/status` with `withCredentials: true`; result cached via `shareReplay(1)` so only one HTTP call is made per session
- `EditPlayerComponent`:
  - Injects `AuthService`; on init calls `isLoggedIn()` in parallel with player data load
  - If not logged in: `myform.disable()` greys out all fields; Save button hidden; yellow lock banner shown: "Sign in to edit player information"
  - If logged in: form fully editable, Save Changes button visible as before

**Modified files:**
- `AuthController.java` (new) — auth status endpoint
- `auth.service.ts` (new) — frontend auth state service
- `edit-player.component.ts` — added `isLoggedIn` flag, `AuthService` injection, disable logic
- `edit-player.component.html` — `*ngIf="isLoggedIn"` on Save button, read-only notice banner
- `edit-player.component.css` — `.readonly-notice` styles

Build verified: `ng build` — clean, no errors.

### STEP 22 — Fix Sign In URL for local dev + show logged-in user in toolbar ✅

**Problem:** Sign In button on local dev pointed to `http://localhost:4200/oauth2/authorization/cognito` (Angular dev server) instead of the Spring Boot backend at `http://localhost:8080`.

**Frontend changes:**

- `auth.service.ts` — Added `backendUrl` computed property: port `4200` → `http://localhost:8080`, otherwise uses `window.location.protocol + host` (production). Exposed `signInUrl = backendUrl + /oauth2/authorization/cognito`. Changed method from `isLoggedIn(): Observable<boolean>` to `getStatus(): Observable<AuthStatus>` returning `{ loggedIn, name? }`.
- `menubar.component.ts` — Injected `AuthService`; subscribed to `getStatus()` in `ngOnInit`; stored result in `authStatus: AuthStatus`.
- `menubar.component.html` — Sign In button now uses `[href]="authService.signInUrl"` (dynamic). When logged in, replaced Sign In button with a user pill showing `account_circle` icon + user's name from Cognito.
- `menubar.component.css` — Added `.toolbar-user` / `.toolbar-username` styles for the logged-in user display.
- `edit-player.component.ts` — Updated call from deprecated `isLoggedIn()` to `getStatus()`.

Build verified: `ng build` — clean, no errors.

### STEP 23 — Mask phone and email for guests on player profile ✅

When a user is not logged in, the phone and email fields in the player profile dialog are masked:
- Phone: `***-***-XXXX` (last 4 digits visible)
- Email: `X***@***.tld` (first char of username + top-level domain visible)

Logged-in users see the real values as before.

**Implementation:** Replaced the two parallel async calls (`getStatus()` + `getPlayerById()`) with a single `combineLatest([...])` so both auth status and player data arrive together before the form is populated. Masking is applied conditionally inside the combined subscription.

**Modified files:**
- `edit-player.component.ts` — Added `combineLatest` import; rewrote `ngOnInit` to use combined observable; added `maskPhone()` and `maskEmail()` private helpers

Build verified: `ng build` — clean, no errors.

### STEP 24 — Home page redesign: News + Season Points Standings ✅

**Database changes:**
- `event_player`: added `id INT AUTO_INCREMENT PRIMARY KEY` + `game_point INT DEFAULT 0`
- `player`: added `pgc_points INT DEFAULT 0`
- Created `news` table: `id`, `title`, `content TEXT`, `publish_date DATE`, `is_active`

**Backend — new files:**
- `EventPlayer.java` — JPA entity for `event_player`
- `News.java` — JPA entity for `news`
- `EventPlayerRepository.java`, `NewsRepository.java`
- `PointsServiceImpl.java` — finds current season, ranks players per event by gross score (pos 1→16 pts … pos 16→1 pt), upserts `event_player.game_point`, sums top-3 per active player → `player.pgc_points`
- `PointsController.java` — `POST /webapi/admin/points/calculate`, `GET /webapi/points/standings`
- `NewsController.java` — `GET /webapi/news`, `GET /webapi/news/all`, `POST/PUT/DELETE /webapi/admin/news/{id}`

**Backend — modified files:**
- `Player.java` — added `pgcPoints` field
- `EventRepository.java` — `findBySeasonIdAndStatusIn()`
- `SeasonRepository.java` — `findCurrentSeason(Date)` JPQL query
- `PlayerRepository.java` — `findByIsActiveTrueOrderByPgcPointsDesc()`

**Frontend:**
- New: `news-representation.ts`, `standing-representation.ts`, `news.component`, `news-dialog.component`
- `player.service.ts` — added 7 new methods for news and points
- `home.component` — rewritten: Section 1 hero, Section 2 news card grid (latest 5 active), Section 3 standings table with Calculate Points button (admin only, gold/silver/bronze rank badges)
- `menubar.component.html` — added News menu item
- `app.module.ts` / `app-routing.module.ts` — registered NewsComponent, route `/news`

Build verified: `ng build` — clean, no errors.

### STEP 25 — Confirm season-scoped point calculation ✅

STEP 24 already scopes calculation to the current season: `findCurrentSeason(today)` returns the season whose `start ≤ today ≤ end`, and only FINISHED/CLOSED events from that season are processed. Points from prior seasons are not included.

**Defensive fix added:** `updatePlayerTotalPoints()` now guards against an empty `eventIds` set (which can occur if no qualifying games exist yet in the season) by short-circuiting to `emptyList()` instead of passing `IN ()` to Hibernate, which can throw on some versions. Active players still get their `pgc_points` reset to 0 in this case, which is the correct behavior for a fresh season.

Backend compile verified: clean.

### DOC — Admin User Manual for Event / Game Workflow ✅

Wrote `USER_MANUAL_EVENT_GAME.md` (repo root, `/Users/ouyan/Documents/pgc-ai/`) covering the
end-to-end admin flow: course setup → create event → roster → generate groups/tees →
Mark as STARTED → upload scores → FINISHED → calculate points, plus tournaments,
awards, photos, a printable checklist, and a troubleshooting table.

Sourced from the code, not assumptions. Behaviours documented that are non-obvious:

- **Status gating.** `Plan a Game` lists only `INIT`/`STARTED`; `Upload Score` lists only
  `STARTED`; score-detail links and points require `FINISHED`/`CLOSED`.
- **`STARTED` is unreachable from the event dialog.** `event-dialog.component.ts` offers
  `INIT, NOTSTART, FINISHED, CLOSED`. Only the *Mark as STARTED* button on the Plan a Game
  page sets it — which is also what triggers `copyPgcHandicapToScores()`.
- **Ordering hazard flagged.** `GamePlanController.generateGroups()` deletes and recreates
  `player_score` rows with `entryPGCHandicap = 0.0`. Marking STARTED *before* generating
  groups therefore loses the snapshot, and since `ScoreServiceImpl.submitPlayerScore()`
  only back-fills the entry handicap when it is `null` (not `0.0`), net score silently
  equals gross. Manual documents: build roster → generate → adjust → Mark as STARTED.
- **`Onboard Player` on the Upload Score page is legacy.** `ScoreServiceImpl.addEventPlayer()`
  hard-codes `tee.setId(10000L)` and ignores the selected event. Manual tells admins to use
  the Plan a Game roster instead.
- **Name matching rule.** First token = first name, second token = last name,
  case-insensitive; extra tokens ignored. Unmatched rows are skipped, not rejected.
- **Group sizing.** `groupSizes()` caps at 4 and converts a remainder of 1 into `[3,2]`
  (9→4,3,2; 13→4,4,3,2) — documented as a table.
- **Points detail.** Countback tie-break (back 9 → last 6 → last 3 → hole 18), tied players
  share the higher rank's points, 16→1 for positions 1–16, best 3 games per player,
  current season only, active players only.
- **Tee sort caveat.** `findByEventIdOrderByTeeName` is alphabetical, so `Tee 10` precedes
  `Tee 2`; UI sorts by tee time first. Naming tip included.

No code changes.

### DEPLOY — 2026-08-07: production was 73 days stale, redeployed ✅

**Problem found.** Production (`pgcgolf.club`) was serving a build from **2026-05-26 11:06** —
UI *and* backend. The Upload Score page was the pre-STEP41 version. Missing from prod:

- `19bc354` (Jul 18) Tournament feature + points/score enhancements — backend
- `28a1b5c` (Jul 21) STEP40 handicap snapshot on START
- `3991ffe` (Jul 21) STEP41 upload to selected event

**Root cause.** The Aug 7 "deploy" never uploaded the new artifact. `ROOT.war` and the backup
it created (`ROOT.war20260807`) were byte-identical — md5 `0db44519…`, 76,965,918 bytes — while
the locally built WAR was `c4763c26…`, 66,316,399 bytes. The old WAR was archived and put back
unchanged; the new one stayed on the laptop.

**Diagnosis method** (reusable): fetch the live bundle and grep for feature strings —
`curl -s https://pgcgolf.club/main.<hash>.js` then grep for `Import scores from a golf-app export`,
`Check User`, `sheet_to_json`. Compare the `src="main.*.js"` hash in the live `index.html` against
`PGCServer/src/main/resources/static/`. Confirm on the server with
`sudo find …/ROOT/WEB-INF/classes/static -printf '%TY-%Tm-%Td %TH:%TM %f\n'`.

**Pre-deploy schema check.** `application.properties` sets `hibernate.hbm2ddl.auto: update` under the
bare `hibernate.*` prefix, which **Spring Boot ignores** (it wants `spring.jpa.hibernate.ddl-auto`),
so prod schema is effectively `none` — no auto-migration. Verified prod MySQL already had everything
the gap code needs before deploying: `tournament` table (id, name, description, season_id, start_date,
end_date — exact match to the entity), `event.tournament_id`, `player_score.entry_pgc_handicap`,
`player.pgc_points`, `event_player`, `event_photo`. No migration required.

**Deploy.** Uploaded the existing verified `target/shiyuan-0.0.1-SNAPSHOT.war` (no rebuild — no source
file was newer than the artifact, and it already had exactly 1 `main.*.js`). Old WAR archived as
`ROOT.war.08072026_1527`.

**Verified after restart:**
- `https://pgcgolf.club/` → `200`; clean catalina.out, `Started ServletInitializer in 10.221 seconds`
- live `index.html` → `main.c36ec27fb495ac08.js`
- served bundle md5 `6ef62983…` == local `src/main/resources/static/` copy, byte for byte
- STEP41 strings all present live; `sheet_to_json` present (SheetJS shipped)
- deployed `EventServiceImpl.class` contains `copyPgcHandicapToScores` (STEP40)
- deployed static holds **1** bundle, not the 12+ stale ones the old WAR carried

**Note.** `USER_MANUAL_EVENT_GAME.md` describes the STEP41 Upload Score page; as of this deploy the
manual matches production.

### DOC — User manual converted to HTML ✅

`USER_MANUAL_EVENT_GAME.html` (29 KB, self-contained — no external CSS/JS/fonts, opens
offline, prints cleanly). Built with `npx marked` for the body, then a wrapper script
(`build_manual_html.py`, kept in the session scratchpad) for styling and two fixes marked
does not handle:

- **Heading ids.** marked v18 emits no `id` on headings, so all 13 table-of-contents links
  would have been dead. The script re-adds them with GitHub slug rules (lowercase, strip
  non-alphanumerics, spaces→hyphens), which reproduces the doubled hyphen that em-dash
  headings produce — matching the anchors the markdown already links to. Verified: 0 broken.
- **Wide tables.** Each `<table>` is wrapped in an `overflow-x:auto` div so the page body
  never scrolls sideways on a phone.

Styling reuses the site's own tokens (`--pgc-blue #1976d2`, `--pgc-green #388e3c`) for a
gradient masthead, blue-tinted table headers, amber callout blockquotes; responsive at
700px and a print stylesheet that avoids page-breaking tables and code blocks.

**Content change:** the site URL in both the .md and .html now points at `https://pgcgolf.club`
instead of `https://www.pgcgolf.club`. The TLS cert covers only the bare domain
(`DNS:pgcgolf.club`, no `www` SAN), so the old link sent readers into a browser security
warning. **The cert itself is still unfixed** — `www.pgcgolf.club` remains broken for anyone
typing it directly. Worth adding the SAN or a redirect at nginx.

Verified: 30 heading ids, 0 broken anchors, 8 tables, 2 code blocks, no unconverted markdown.

### STEP42 — Course-based round handicap ✅

Spec: `docs/superpowers/specs/2026-08-08-course-based-handicap-design.md`. Built test-first.

**The bug.** `submitPlayerScore` computed the USGA Score Differential with frozen constants
(`SLOPE_RATING = 126.0`, `COURSE_RATING = 73.4`) — those are Charleston Springs South,
played by exactly one of nine events. Because `pgc_handicap = (differential + entry) / 2`
on every submission, the error fed forward into every player's running handicap, and from
there into net scores and the Plan a Game grouping strategies. Measured against production:
handicaps understated by ~1.7–2.6 strokes on 7 of 8 scored events (worst: 2025 PGC Classic,
+2.57).

**Changes:**
- `HandicapCalculator` (new) — pure, no Spring/JPA. `scoreDifferential(gross, rating, slope)`
  = `(gross - rating) * 113 / slope`, rounded `HALF_UP` to 1 decimal per USGA. No fallback
  values by design; rejects `slope <= 0`.
- `InvalidCourseDataException` (new) — carries an admin-readable message.
- `ScoreServiceImpl.submitPlayerScore` — now `@Transactional`; resolves and validates the
  event's course **before the loop** so a bad setup writes nothing; uses the calculator.
  Also fixed a latent NPE: a player with a null `pgc_handicap` used to blow up on unboxing
  in the averaging step and get silently skipped by the catch-all. Now their first round
  stores the differential as both `current_handicap` and `pgc_handicap`, `net_score` null.
- `ScoreController.handleInvalidCourseData` — `@ExceptionHandler` → 400 + message.
- `PGCUI upload-score.component` — `subscribe` had no error callback and no success
  feedback, so a 400 would have been invisible. Now shows the server message on failure,
  a count on success, and disables the button while in flight.

**Decisions** (agreed before implementing): reject rather than fall back on missing course
data; **fix forward only, no historical backfill**; round to 1 decimal.

**Verification:**
- 20/20 tests pass (`mvn clean test -Dspring.profiles.active=local`). 16 new: 6 calculator,
  9 service, 1 controller. Every one watched fail first — the service tests' RED showed
  `expected: <16.7> but was: <14.887301587301582>`, i.e. the old hardcoded output.
- UI `npm run build` exit 0, zero error lines (9 warnings, all pre-existing NG8102 in other
  components).
- End-to-end on local MySQL: event 1152 (Wild Turkey, 132/71.7), gross 90 for Ron Zhang →
  `current_handicap` **15.70** (old formula would give 14.9), `net_score` 83.18 = 90 − 6.82,
  `pgc_handicap` 11.26 = (15.7 + 6.82)/2. Rows restored afterwards.
- Rejection path over HTTP: event 1 (no course) → **400** `Event 'PlaceHolder Event' has no
  course assigned. Set the course in Game Management before uploading scores.`;
  unknown event → **400** `Event 99999 was not found.`

**Note on `mvn clean test` without a profile:** `ShiyuanApplicationTests.contextLoads` fails
with `Access denied for user 'ouyan'@'localhost'` because the default `application.properties`
carries the *production* DB credentials. Pre-existing and unrelated — it passes with
`-Dspring.profiles.active=local`.

**Not done (deliberate):** historical backfill; endpoint auth — `/webapi/event/scores` is
still `permitAll` and never calls `checkPrincipal`, so an unauthenticated caller can post
scores. Both tracked in §8 of the spec.

Not committed, not deployed.

### STEP42b — Require login to submit scores ✅

One line in `SecurityConfiguration.filterChain`:

```java
.requestMatchers(HttpMethod.POST, "/webapi/event/scores").authenticated()
```

Previously only `/webapi/admin/**` was authenticated, so `POST /webapi/event/scores` was
`permitAll` — any unauthenticated caller could write `player_score` rows and shift every
player's running `pgc_handicap`. Proven during STEP42 verification: an anonymous curl
successfully scored a player.

No new dependency and no controller change. The existing
`defaultAuthenticationEntryPointFor(/webapi/**)` already answers **401** instead of
redirecting to Cognito, so XHR callers get a usable status; the UI error handler added in
STEP42 surfaces it.

Scoped to POST only, so `GET /webapi/event/score` (the public score-detail modal) is
unaffected.

**Verified against the running app (local profile):**
| Request | Before | After |
|---|---|---|
| `POST /webapi/event/scores` anonymous | 200 (wrote data) | **401** |
| `GET /webapi/event/score` | 200 | 200 |
| `GET /webapi/events`, `/webapi/courses`, `/webapi/points/standings` | 200 | 200 |

Confirmed the blocked POST wrote nothing (player_score 2400 still `score=0`,
`current_handicap=0.00`, `pgc_handicap=6.82`). Full suite 20/20 green.

**Still open:** `POST /webapi/event/create/playerscores` (the legacy "Onboard Player"
button) is also `permitAll` and also writes `player_score` rows — left alone, as the
request was scoped to the score-submit endpoint only.

Not committed, not deployed.

### STEP43 — Rolling PGC handicap from the last 5 rounds ✅

Spec: `docs/superpowers/specs/2026-08-08-rolling-pgc-handicap-design.md`. Built test-first.

**Old rule.** `submitPlayerScore` wrote `pgc_handicap = (thisRoundDifferential + entry) / 2`
at upload time — a decaying average where the newest round is 50% of the index, the one
before 25%, and so on. One bad round moved a player a long way, and uploading a score
immediately rewrote the index.

**New rule.** `pgc_handicap` = mean of the player's most recent **up to 5** differentials
from FINISHED/CLOSED events (gross > 0, differential present), across all seasons, rounded
to 2dp. Fewer than 5 rounds averages what exists — the common case, since only 21 of 92
players have 5. A player with no qualifying rounds keeps their existing handicap.

**Changes:**
- `HandicapCalculator.rollingHandicap(List<Double>)` + `ROUNDS_COUNTED = 5` — pure; takes
  the first ≤5, means them, HALF_UP to 2dp; rejects null/empty.
- `PlayerScoreRepository.findRecentDifferentials(playerId, statuses, Pageable)` — JPQL,
  newest first by `event_date DESC, event.id DESC` (`score_date` is NULL on all 319 scored
  rows, so it cannot order anything), `Pageable` supplies the LIMIT.
- `HandicapService` / `HandicapServiceImpl` — `recalculateForEvent(eventId)` and
  `recalculateAll()`, both `@Transactional`, both returning the count updated.
- `EventController` — recalculates on any transition **into** FINISHED/CLOSED, hooked into
  *both* `PUT /events/{id}` and `PUT /events/{id}/status`. The Edit dialog uses the former,
  so hooking only the status endpoint (STEP40's pattern) would never have fired.
  FINISHED→CLOSED is deliberately not a trigger; the qualifying set is unchanged.
- `POST /webapi/admin/handicaps/recalculate` — manual re-run after a score correction.
- `ScoreServiceImpl` — no longer writes `pgc_handicap` at all. One rule owns the column.
- `PGCUI` — "Recalculate Handicaps" button beside Calculate Points on the Home page.

**Decisions:** trigger on transition **and** a manual button; count FINISHED **and** CLOSED
(archiving a finished event must not silently change handicaps — and points already treats
them alike); all seasons, rolling; **no backfill** of historical differentials.

**Known consequence, accepted.** The average reads `current_handicap`, still wrong for 7 of
8 past events (pre-STEP42 hardcoded course, ~1.7–2.6 strokes low). So the cutover recalc
moves handicaps using values we know are low — e.g. Peter Hou 13.16 → 10.63, roughly 2
strokes of which is the old bug rather than the new rule. Nothing becomes *newly* wrong (the
stored values came from the same polluted differentials), and it self-corrects as fresh
rounds land. Backfilling `current_handicap` stays a clean separate one-off.

**Verification:**
- 35/35 tests pass (`mvn clean test -Dspring.profiles.active=local`); 15 new this step.
  Every one watched fail first — removing the submit-time write showed `13.35`, exactly the
  old `(16.7 + 10.0)/2`.
- UI build exit 0, 0 errors; NG8102 count unchanged at 6 and the home.component.css budget
  warning confirmed pre-existing.
- **JPQL proven against real data** via a temporary read-only test (since deleted): for
  Christine He the query returned `[15.78, 11.3, 15.78, 10.4, 11.3]` — the newest 5 of her 8
  qualifying rounds, correctly ordered — giving 12.91, matching an independent SQL
  computation.
- `POST /webapi/admin/handicaps/recalculate` anonymous → **401**, handicaps unchanged.
- Local DB left untouched: players 2001/2076/2082 still 13.54 / 6.82 / 6.04.

**Cutover still to do:** press "Recalculate Handicaps" once after deploy so existing
FINISHED events are reflected. Until then stored handicaps keep their old-rule values.

Not committed, not deployed.

### DEPLOY — 2026-08-08: STEP42 + endpoint auth + STEP43 to production ✅

Fresh build (the previous WAR predated all of this work). Backed up production MySQL first,
since the pending cutover overwrites every player's handicap irreversibly:
`db-dump/pgc-20260808-224457-server-backup.sql.gz` — gzip verified, 13 CREATE TABLE,
`-- Dump completed` trailer present.

**Build:** UI `npm run build` exit 0 → `main.aa768a6a381ca720.js`; static folder replaced;
`mvn -DskipTests clean package` → 63 MB WAR with **exactly 1** bundle. Verified the new code
was actually inside the WAR before shipping (`HandicapService`, `HandicapServiceImpl`,
`HandicapCalculator.rollingHandicap`, `InvalidCourseDataException`,
`handicaps/recalculate` in `EventController`, `webapi/event/scores` in
`SecurityConfiguration`) — the STEP42 post-mortem was a WAR that shipped stale content.

**Deploy:** md5 `6123a254…` identical local → `/tmp` → deployed path. Old WAR archived as
`ROOT.war.08082026_2246`. Tomcat clean start, `Started ServletInitializer in 10.164 seconds`,
no errors from the new PID.

**Verified live on https://pgcgolf.club:**
| Check | Result |
|---|---|
| Site | 200 |
| index.html bundle | `main.aa768a6a381ca720.js` |
| Served bundle vs local | md5 `a1dafcf0…` — identical |
| Anonymous `POST /webapi/event/scores` | **401** (was 200 and wrote data) |
| Anonymous `POST /webapi/admin/handicaps/recalculate` | **401** |
| `/webapi/events`, `/webapi/courses`, `GET /webapi/event/score` | 200 |
| UI strings live | Recalculate Handicaps, handicaps/recalculate, Scores submitted for, Check User |

Production DB unchanged by the deploy (2001/2076/2082 still 13.54 / 6.82 / 6.04).

**OUTSTANDING — cutover not yet done.** STEP43 is live but stored handicaps still hold
old-rule values. Signed-in admin must press **Recalculate Handicaps** on the Home page once.
That will move handicaps using differentials still affected by the pre-STEP42 hardcoded
course (~1.7–2.6 strokes low) — expected and previously accepted; backup above is the
rollback path.

Still not committed to git.

### BUG — 500 on score upload after the 2026-08-08 deploy: root cause ✅

**Symptom.** `POST /webapi/event/scores` → 500 when uploading to event 1152.

**Exception** (catalina.out, PID 2771582, 02:56:38Z):
```
SQL Error: 1264, SQLState: 22001
Data truncation: Out of range value for column 'net_score' at row 1
```

**Root cause — four links:**

1. **The column cannot hold the value.** `player_score.net_score` is `decimal(4,2)` →
   hard maximum **99.99**. Proven directly against local MySQL:
   `UPDATE player_score SET net_score=105.00` → `ERROR 1264 (22003) Out of range value for
   column 'net_score'`; `99.99` is accepted.
2. **Event 1152 has no entry handicaps.** All 16 rows have `entry_pgc_handicap = 0.00`, so
   `net_score = gross − 0 = gross`. The event reached STARTED in July, *before* STEP40 was
   deployed (it sat in the 73-day stale-deploy gap), so `copyPgcHandicapToScores` never ran
   for it.
3. **Gross scores over 100 are normal here.** 64 of 319 scored rows (20%) are ≥ 100, max
   **124**. Any such player overflows the column once the handicap is 0.
4. **Why it is a 500 now and was not before.** STEP42 added `@Transactional` to
   `submitPlayerScore`. Without it, each `save()` committed on its own and the truncation
   surfaced *inside* the loop's `catch (Exception)`, which swallowed it and skipped the
   player — HTTP 200 with data silently missing. The stack trace confirms the new path:
   `TransactionInterceptor → commitTransactionAfterReturning → JpaTransactionManager.doCommit`
   — the flush now happens after the method returns, outside the catch.
   Historical proof of the old silent behaviour: **172** `could not be processed` lines in
   catalina.out.

So the 500 is new; the underlying data loss is old and was invisible.

**The snapshot alone is not a sufficient fix.** Even with handicaps restored, a low-handicap
player overflows at a realistic gross: Sean Sun (1.43) at 101, Ron Zhang (6.82) at 107 —
against a historical max of 124.

**Recommended fix (NOT yet applied — needs approval, production schema change):**
```sql
ALTER TABLE player_score MODIFY COLUMN net_score DECIMAL(5,2);
```
Widens the range to ±999.99. `current_handicap`/`entry_pgc_handicap`/`pgc_handicap` at
`decimal(4,2)` are fine — the largest realistic differential is ~46.

Also outstanding for event 1152: re-trigger the handicap snapshot (Plan a Game → **Mark as
STARTED**), otherwise every net score for that event equals the gross.

### FIX — widened `net_score` on production ✅ (2026-08-08)

Applied, with the pre-deploy backup `db-dump/pgc-20260808-224457-server-backup.sql.gz` as the
rollback path:

```sql
ALTER TABLE player_score MODIFY COLUMN net_score DECIMAL(5,2) NULL;
```

`NULL` stated explicitly rather than relying on MySQL's default, so the column's existing
nullability was preserved rather than assumed.

| | Before | After |
|---|---|---|
| Type | `decimal(4,2)` (max 99.99) | **`decimal(5,2)`** (max 999.99) |
| Nullable | YES | YES |
| Rows / non-null | 338 / 330 | 338 / 330 |
| min / max net_score | 0.00 / 94.00 | 0.00 / 94.00 |

Data untouched — widening a DECIMAL rewrites no values. Confirmed the previously-failing
value now stores: `UPDATE ... SET net_score=124.00` succeeded (rolled back).

**Still outstanding for event 1152:** `entry_pgc_handicap` is 0.00 for all 16 players because
the event reached STARTED before STEP40 shipped. Until an admin re-presses **Mark as STARTED**
in Plan a Game, every net score for that event will equal the gross. The schema fix unblocks
the upload; it does not make those net scores meaningful.

**Also still outstanding:** the STEP43 cutover press of **Recalculate Handicaps**, and the
fact that nothing from STEP42/43 is committed to git.

### BUG — 500 deleting event 1152: root cause ✅ (2026-08-08)

**Symptom.** `DELETE /webapi/admin/events/1152` → 500.

**Exception:**
```
SQL Error: 1451, SQLState: 23000
Cannot delete or update a parent row: a foreign key constraint fails
(`pgc`.`event_player`, CONSTRAINT `event_player_event_FK`
 FOREIGN KEY (`event_id`) REFERENCES `event` (`id`))   [delete from event where id=?]
```

**Root cause.** `EventServiceImpl.deleteEventById()` is a bare
`eventRepository.deleteById(id)`. `Event` maps exactly one child collection —
`teeList` with `cascade=ALL, orphanRemoval=true` — so tees and their `player_score`
rows are cleaned up by JPA. But **four** tables carry an FK to `event`, and three of
them are not mapped on the entity at all:

| Table | FK | Mapped on Event? | Rows for 1152 |
|---|---|---|---|
| `tee` | `tee_event_FK` | yes (cascade ALL) | 4 |
| `event_player` | `event_player_event_FK` | **no** | **16** ← fails here |
| `reward` | `reward_event_FK` | **no** | 0 |
| `event_photo` | `fk_photo_event` | **no** | 0 |

MySQL rejects the parent delete at the first unmapped child. Pre-existing since the game
plan feature (STEP33) introduced `event_player` without updating the delete path —
unrelated to STEP42/43.

**Recommended fix:** make `deleteEventById` `@Transactional` and remove the unmapped
children first (`event_player`, `reward`, `event_photo`), then the event — letting the
existing `teeList` cascade handle tees/scores. No schema change, no new EAGER collections
on an entity that already eager-loads `teeList`. Deleting an event that no longer exists
should also 404 rather than 500.

**NOT applied — flagged to the user first:** event 1152 holds **16 `player_score` rows, 10
of which have real scores** (survivors of pre-STEP42 partial uploads, when each save
committed independently). Deleting the event destroys them. It is also linked to
`tournament_id = 2`.

### FIX — event delete now clears uncascaded children ✅ (2026-08-08)

Built test-first for the FK violation diagnosed above.

**Changes:**
- `EventPlayerRepository`, `RewardRepository`, `EventPhotoRepository` — added
  `@Modifying @Query` bulk `deleteByEventId(eventId)`, matching the existing
  `PlayerScoreRepository.deleteByEventId` style (no entity loading).
- `EventServiceImpl.deleteEventById` — now `@Transactional`; checks existence, then clears
  `event_player` → `reward` → `event_photo`, then deletes the event. Tees and their
  `player_score` rows continue to go via the existing `teeList` cascade.
- `EventNotFoundException` (new) + `@ExceptionHandler` in `EventController` → **404**
  instead of a 500 leaking out of the persistence layer.

Deliberately did **not** add `@OneToMany` collections for these children on `Event`: the
entity already EAGER-loads `teeList`, and three more eager collections would be loaded on
every event read for the benefit of one delete path.

**Verification:**
- 37/37 tests pass. 2 new unit tests watched fail first (`Wanted but not invoked` for the
  child deletes; `Expected EventNotFoundException ... nothing was thrown`).
- The unit tests assert call *order* against mocks, which cannot catch a constraint MySQL
  actually enforces — so this was also proven against the real schema with a temporary
  `@SpringBootTest @Transactional` test (rollback-only, since deleted): deleting local event
  1152 with **16 roster rows / 4 tees / 16 player_scores** succeeded, leaving 0 orphaned
  roster rows and 0 orphaned tees. Confirmed afterwards that local 1152 was still intact
  (1 event / 16 / 4 / 16), i.e. the rollback held and nothing was destroyed to test this.

**Not deployed yet.** Production event 1152 is still undeletable until this ships, and
deleting it will destroy 16 player_score rows, 10 of them scored.

### DECISION — keep `event_player` (2026-08-08)

Investigated whether `event_player` and its JPA classes could be dropped. **Decision: keep.**

It is a pure join table (`id`, `event_id`, `player_id` — no other columns) with **8 live call
sites**, all in `GamePlanController` plus the delete added today: list roster, dedupe on add,
save, remove, feed group generation, compute unassigned players. Dropping it breaks step 2 of
Plan a Game.

It *is* redundant once groups exist — `player_score` via `tee → event` already records who is
in an event — and the two are already inconsistent: only events 1151 and 1152 have roster rows
(16 each, matching their score rows); the seven older events have **0** roster rows against
39–52 score rows, because they predate the Game Plan feature. Its only unique role is the
window before groups are generated.

Consolidating onto `player_score` with `tee_id = NULL` (the column is nullable) would remove
the duplication and let the table go, but it is a real refactor of the roster flow plus a
migration of 32 rows. Not worth doing mid-season with an event in STARTED. Recorded as a
future option, not planned.

**STILL OPEN — data-loss hazard from this duplication.**
`GamePlanController.generateGroups` (lines ~85-93) deletes before it validates:

```java
playerScoreRepository.deleteByEventId(eventId);   // scores destroyed
teeRepository.deleteByEventId(eventId);
List<Player> players = eventPlayerRepository.findByEventId(eventId)...
if (players.isEmpty()) { return ResponseEntity.badRequest().build(); }
```

`badRequest()` is a *return*, not a thrown exception, so `@Transactional` commits the deletes.
Generate Groups against an empty roster therefore wipes that event's scores and tees and
returns 400. Applies to 7 of 9 events today. The UI limits reach (step 1 lists only
INIT/STARTED) but the endpoint is directly callable, and it applies to event 1152 as soon as
its roster is emptied — that event currently holds 10 scored rows.

Fix is to move the empty check above the deletes. Not yet applied — awaiting go-ahead.

### DEPLOY — 2026-08-08 23:30: event-delete fix to production ✅

Backend-only change (event delete clears uncascaded children + 404 for a missing event).

Fresh backup first, since scores had been uploaded since the 22:44 one:
`db-dump/pgc-20260808-232941-server-backup.sql.gz` — gzip verified, 13 CREATE TABLE,
`-- Dump completed` trailer.

**Skipped the UI rebuild deliberately.** No UI source was newer than the deployed bundle and
`src/main/resources/static/` already held exactly `main.aa768a6a381ca720.js` (the live one), so
step 2 of the deploy skill — `rm -rf static/*` — was avoided rather than run needlessly. The
WAR still shipped exactly 1 bundle, unchanged.

Verified the fix was inside the WAR before uploading: `EventNotFoundException.class` present,
`deleteByEventId` in `EventPlayerRepository`, `EventNotFoundException` referenced from
`EventServiceImpl`.

**Deploy:** md5 `8914e12f…` identical local → `/tmp` → deployed. Old WAR archived as
`ROOT.war.08082026_2330`. Clean start, PID 2779648, `Started ServletInitializer in 10.439
seconds`.

**Verified live:** site 200; bundle unchanged as expected; `DELETE /webapi/admin/events/99999`
→ **401** (auth still enforced, and no 500); `/webapi/events` and `/webapi/courses` 200.
Data intact: 10 events / 338 player_score / 32 event_player / 65 tee.

The 404-for-missing-event path could not be exercised from here — the endpoint requires a
signed-in admin, so an anonymous probe correctly stops at 401. Covered by unit tests and the
temporary real-schema test run before deploy.

Event 1152 is now deletable. Doing so still destroys 16 player_score rows (10 scored) and
removes a round from tournament 2 — backup above is the rollback.

### STEP44 — Latest News: show the full article ✅ (2026-09-26)

**Problem.** Three separate frontend causes, none of them backend:
1. `.news-content` had `-webkit-line-clamp: 4; overflow: hidden` — the home card truncated.
2. The card had **no click handler at all**, so "click to see the rest" had no behaviour to fix,
   only one to add.
3. Even unclamped it would have rendered wrong: the live article (news id 3, 684 chars) holds
   **10 newlines**, and interpolating into a `<p>` collapses them to spaces — a multi-paragraph
   piece came out as one run-on block.

Backend is clean: `news.content` is `TEXT`, `GET /webapi/news` returns it whole.

**Decisions (user):** expand *in place* with Read more / Show less rather than a dialog;
home page only.

**Changes — `PGCUI` home component only, no backend:**
- `home.component.ts` — `expandedNews: Set<number>`, `isNewsExpanded()`, `toggleNews()`,
  `newsNeedsToggle()`.
- `home.component.html` — `[class.expanded]` on the content, plus a Read more / Show less
  button rendered only when the article is long enough, with `aria-expanded`.
- `home.component.css` — clamp now only applies when not `.expanded`; added
  **`white-space: pre-line`** (the fix for cause 3) and `overflow-wrap: break-word`;
  link-styled toggle with a `:focus-visible` ring.

`newsNeedsToggle` judges from content (>200 chars or contains a newline) rather than measuring
rendered height — measuring needs an after-view-checked hook and invites
`ExpressionChangedAfterItHasBeenChecked` for no real gain at this list size. Tradeoff: at a very
narrow viewport a ~150-char item could clamp without offering the toggle.

**Verification:** `npm run build` exit 0, **0 errors**, NG8102 count unchanged at 6 (pre-existing,
line numbers shifted only). Confirmed in the built bundle `main.0d54fe94f9c78ecc.js`: Read more /
Show less / `isNewsExpanded` / `newsNeedsToggle` all present; compiled CSS shows the base clamp
with `white-space:pre-line` and `.news-content.expanded{display:block;-webkit-line-clamp:none;
overflow:visible}`. Heuristic checked against the real production rows — id 3 (684 chars,
newline, the only active one) gets the toggle; ids 1 and 2 (87/100 chars) would not.

**Not visually confirmed in a browser** — no rendering check was possible from here; the above is
build + built-artifact + data verification only.

Not deployed, not committed.

### DEPLOY — 2026-09-26: STEP44 (news Read more) to production ✅

Backup first (previous one was seven weeks old):
`db-dump/pgc-20260926-112603-server-backup.sql.gz` — gzip verified, 13 CREATE TABLE,
`-- Dump completed` trailer.

**Build.** UI change, so the full path this time: `npm run build` (exit 0, 0 errors) → checked
dist was current and `index.html` existed *before* the destructive `rm -rf static/*` → copied →
`mvn -DskipTests clean package`. 63 MB WAR, **exactly 1** bundle, `index.html` pointing at
`main.0d54fe94f9c78ecc.js`.

Tests skipped in the package step: no backend source changed since the green 37/37 run, and this
release is UI-only. Verified the backend classes from the previous deploy were still in the WAR
(`HandicapService`, `HandicapServiceImpl`, `EventNotFoundException`, `InvalidCourseDataException`)
and that the new UI strings were genuinely inside the WAR's bundle before uploading — the
2026-08-07 post-mortem was a WAR that shipped stale content.

**Deploy.** md5 `4f214b3d…` identical local → `/tmp` → deployed path. Old WAR archived as
`ROOT.war.09262026_1127`. Clean start, PID 832926, `Started ServletInitializer in 9.97 seconds`.

**Verified live on https://pgcgolf.club:**
| Check | Result |
|---|---|
| Site | 200 |
| index.html bundle | `main.0d54fe94f9c78ecc.js` |
| Served bundle vs local | md5 `7c9f4e5e…` — identical |
| Read more / Show less / newsNeedsToggle / pre-line | all present in the served bundle |
| `GET /webapi/news` | returns article id 3 with literal `\n\n` — the newlines `pre-line` now preserves |

Rendering itself is for the user to confirm in a browser; everything verifiable without one checks out.

Still open: `generateGroups` delete-before-validate hazard; STEP43 **Recalculate Handicaps**
cutover press; nothing committed to git.

### STEP44b — Carousel shows the latest 15 photos ✅ (2026-09-26)

**The count was the smaller half of the problem.** The carousel called
`GET /webapi/photos/random?count=20`, whose implementation was
`photoRepository.findAll()` → `Collections.shuffle(all)` → take 20. So it was showing a
*random* sample of the entire archive, not recent photos at all.

**"Latest" clarified by the user mid-change:** photos from the most recent **events** (by event
date), not the most recent uploads — an old outing uploaded late must not jump to the front.

**Changes:**
- `EventPhotoRepository.findLatestByEventDate(Pageable)` — JPQL
  `ORDER BY ep.event.eventDate DESC, ep.uploadedAt DESC, ep.id DESC`. Ordering and limiting now
  happen in the database instead of loading every row to shuffle in memory.
- `PhotoController` — `/webapi/photos/random` replaced by **`/webapi/photos/latest`**, default
  `CAROUSEL_PHOTO_COUNT = 15`, guards a non-positive count. The old path is gone rather than left
  behind as a misleadingly-named dead endpoint; it had exactly one consumer.
- `player.service.ts` — `getCarouselPhotos(count = 15)` → `/webapi/photos/latest`.
- `home.component.ts` — passes 15.

**Verification:**
- 40/40 backend tests (3 new for the carousel, each watched fail first — the retarget from
  upload-order to event-order produced a fresh RED before the fix).
- Mocked tests can't prove a JPQL join ordering, so it was checked against real data with a
  temporary read-only `@SpringBootTest @Transactional` test (since deleted): returned exactly 15,
  the 7 photos from event 1151 (2026-07-17) first, then 8 from 1150 (2026-05-10), event dates
  strictly descending, newest upload first within each event.
- UI build exit 0, 0 errors, NG8102 unchanged at 6. New bundle `main.b3972ee5de609d1e.js`
  contains `photos/latest` and no longer references `photos/random`.

In production this will surface 6 photos from the Maryland Crab Cup (2026-09-19), 7 from Crystal
Springs (2026-07-17) and 2 from 揭幕战 (2026-05-10) = 15.

Not deployed, not committed.

### DEPLOY — 2026-09-26 13:09: carousel latest-15 to production ✅

Backup first: `db-dump/pgc-20260926-130750-server-backup.sql.gz` — gzip verified, 13 tables,
`-- Dump completed` trailer.

Backend **and** UI changed together (the endpoint was renamed), so they had to ship in one WAR —
a UI-only deploy would have broken the carousel outright. Verified both halves were inside the
WAR before uploading: bundle calls `photos/latest` and no longer `photos/random`; compiled
`PhotoController` contains `webapi/photos/latest` and not `webapi/photos/random`;
`EventPhotoRepository` contains `findLatestByEventDate`.

**Deploy:** md5 `b9538852…` identical local → `/tmp` → deployed. Old WAR archived as
`ROOT.war.09262026_1309`. Clean start, PID 848653, `Started ServletInitializer in 10.104 seconds`.

**Verified live:**
| Check | Result |
|---|---|
| Site | 200 |
| Bundle | `main.b3972ee5de609d1e.js` |
| `GET /webapi/photos/latest?count=15` | **15 photos** |
| Grouping | 6 Maryland Crab Cup (09-19), 7 Crystal Springs (07-17), 2 揭幕战 (05-10) |
| Order | all 6 Crab Cup first, then Crystal Springs — newest event first, as intended |

Matches the pre-deploy prediction exactly.

**Note on the removed endpoint:** `/webapi/photos/random` answers **200, not 404** — but it
returns `text/html` (index.html), not photo JSON. `SpaForwardController` catches unmatched paths
and forwards them to the Angular app, so every removed API path will look like a 200 HTML page
rather than a clean 404. The endpoint is genuinely gone; the status code is just the SPA
fallback. Worth knowing when probing for removed APIs — status alone is misleading here, check
the content type.

Still open: `generateGroups` delete-before-validate hazard; STEP43 **Recalculate Handicaps**
cutover press; nothing committed to git.

### OPS — nginx TLS certificate audit (2026-10-18)

**Question: is the certificate expired? No — but it expires in 16 days and nothing will renew it.**

Live cert served by `pgcgolf.club:443`: Let's Encrypt, valid **Aug 5 → Nov 3 2026 13:37 UTC**,
SAN `DNS:pgcgolf.club` only.

**The real problem is renewal automation — there is none.**
`certbot 3.0.0` is installed at `/opt/certbot/bin/certbot` but is **not on PATH**, and there is
**no systemd timer, no root crontab entry and nothing in /etc/cron.d**. Let's Encrypt renews at
30 days remaining (~Oct 4); that window passed with no renewal. Left alone, HTTPS breaks on
**Nov 3**. The renewal config is otherwise sound (`authenticator = nginx`, `installer = nginx`,
ECDSA), so `certbot renew` should just work once something schedules it.

**Three lineages on disk — and this explains the August `www` failure:**
| Lineage | Expires | SAN | Used by nginx |
|---|---|---|---|
| `pgcgolf.club-0001` | Nov 3 2026 (16 days) | `pgcgolf.club` | **yes** |
| `pgcgolf.club` | **Aug 4 2026 (expired 75 days ago)** | `pgcgolf.club` + `www.pgcgolf.club` | no |
| `www.pgcgolf.club` | Nov 3 2026 (16 days) | `www.pgcgolf.club` | no |

The original combined cert covering both names expired Aug 4. The Aug 5 re-issue split it into
two single-name lineages and nginx was pointed at the apex-only one — which is exactly the
`www.pgcgolf.club` TLS failure hit on 2026-08-07 and worked around by changing the user manual's
link to the bare domain. nginx's `server_name` already lists `pgcgolf.club www.pgcgolf.club`
(nginx.conf:39, 123), so only `ssl_certificate` (nginx.conf:67-68) is wrong.

**Recommended, not applied — production TLS, awaiting go-ahead:**
1. Renew now: `sudo /opt/certbot/bin/certbot renew`
2. Automate: cron/systemd running `certbot renew --quiet` twice daily with a
   `--deploy-hook "systemctl reload nginx"`
3. Reissue one cert covering both names
   (`certbot --nginx -d pgcgolf.club -d www.pgcgolf.club`), repoint nginx, retire the two stale
   lineages — fixes `www` permanently and removes the split that caused this.

Read-only audit; nothing on the server was changed.

### OPS — certificate renewed (2026-10-18) ✅

Ran item #1 of the TLS audit. Safety first: backed up `nginx.conf` to
`nginx.conf.bak_20261018_1307`, confirmed `nginx -t` passed, recorded every lineage's serial,
then ran `certbot renew --dry-run`.

**The dry run was worth doing** — it predicted exactly what happened:
`pgcgolf.club-0001` and `www.pgcgolf.club` simulated success, while the stale `pgcgolf.club`
lineage failed with *"Could not bind TCP port 80"*. That lineage's renewal config uses the
**standalone** authenticator, which tries to bind port 80 that nginx already holds. It is
expired and unused by nginx, so it was excluded with `--cert-name` rather than allowed to
produce a noisy failure.

**Result — the certificate nginx actually serves:**
| | Before | After |
|---|---|---|
| Expires | Nov 3 2026 (16 days) | **Jan 16 2027 (89 days)** |
| Serial | `05FBAF0C…7445` | `061D73CC…E62A` |
| SAN | `pgcgolf.club` | `pgcgolf.club` (unchanged) |

certbot reloaded nginx itself; site verified **HTTP 200** during and after. Confirmed by
reading the live cert off port 443, not just certbot's own output.

Note for next time: certbot imposes a *randomized delay* on non-interactive renewals
("random delay of 397 seconds") — a renew command that appears to hang for several minutes is
usually just sleeping, not stuck.

**Unchanged and still outstanding:** no renewal automation exists (#2) — this was a manual
renewal, so the clock simply restarts; and `www.pgcgolf.club` still is not covered by the
served certificate (#3).

### STEP45 — Player score history ✅ (2026-10-21)

Spec: `docs/superpowers/specs/2026-10-21-player-score-history-design.md`. Layout chosen from two
browser mockups built on real data (kept in `.superpowers/brainstorm/`).

**Net new** — nothing called "score history" existed; "Profile → score history" was not in the app.

**Decisions:** rounds listed with one opening on tap (user, from mockup B); **all** rounds, not a
last-10 window (user); public, no sign-in (user). Mine: no new chart dependency, hand-rolled CSS
bars; server converts offsets to strokes so the arithmetic is unit-tested; FINISHED/CLOSED only;
the Score History button closes the profile dialog and opens the history, rather than stacking two.

**The crux:** `player_score.hole_1…18` store **offsets from par**, not strokes. Showing a real 5 on
a par 4 needs the course's per-hole par — verified available, all 9 courses have `hole_1…18`
populated and each set sums exactly to `par_number`.

**Changes — backend:** `PlayerScoreHistory` DTO (+ nested `Round`), `ScoreHistoryService` /
`Impl`, `PlayerNotFoundException`, `PlayerScoreRepository.findHistoryForPlayer`, and a public
`GET /webapi/players/{id}/score-history` with an `@ExceptionHandler` → 404.
**Frontend:** `score-history` component (dialog), typed `player-score-history.ts`,
`getScoreHistory()`, a Score History button in the profile dialog, and the handoff in
`player.component.ts`.

A missing course, or a course without per-hole par, degrades to showing offsets with the par row
hidden rather than failing the request — one bad course should not blank out a member's history.

**Verification:**
- **50/50 backend tests** (10 new, each watched fail first).
- **Real-data check** (temporary read-only test, since deleted): all 8 of Yan Ou's real rounds
  reconcile — converted strokes add back to the gross already stored, across 6 courses with
  different pars; dates descend; the hand-computed 2026-07-17 Ballyowen round matched Out 44 /
  In 51 / 1 birdie / 6 pars.
- **Over HTTP, unauthenticated:** 200 with 8 rounds, best 89 / avg 96.5 / latest 95; newest round
  returns pars `[4,4,5,3,5,3,...]` and strokes `[4,6,5,5,5,5,...]`, Out 44 + In 51 = 95 = stored
  gross; **all 8 reconcile**. Unknown player → **404** "Player 99999 was not found."
- UI build exit 0, 0 errors. Two NG8107 warnings I introduced (redundant `?.` inside an `*ngIf`
  that already narrows) were removed; warning count is back to the pre-existing 9 with **zero** in
  the new component.

Not deployed, not committed.

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

### STEP45b — score history scaled up for desktop ✅ (2026-10-21)

Reported: the scorecard reads fine on mobile but too small on a computer.

**Cause:** the stylesheet was written mobile-first and its only media query
(`max-width: 600px`) shrinks things *further* — nothing ever scaled up. The dialog was also
capped at 860px, leaving most of a wide screen empty.

**Change (PGCUI only):** dialog 860px → 1180px (`maxWidth: 96vw` still caps small screens);
at ≥900px larger table text and padding, a `min-width` on hole columns so they cannot collapse
to a single digit, taller chart with unrotated labels, bigger tiles and rows, and the **two
nines side by side** instead of stacked so the whole card reads at once; a further step up at
≥1250px. Base styles and the `max-width: 600px` query untouched, so mobile is unchanged.

**Verification:** build exit 0, 0 errors, warnings still at the baseline 9 with none in the
component. Deployed (`d61aaa38…`, old WAR archived as `ROOT.war.10212026_2033`); confirmed live
that the served bundle contains both media queries and the side-by-side rule, and the
score-history endpoint still returns 200.

Note: an initial check for the media query reported 0 matches — the grep pattern omitted a
space the minifier keeps (`@media (min-width: 900px){`). The CSS was present all along. Worth
remembering when grepping minified CSS.

Committed and pushed: PGCUI `f9453884`.

### STEP46 — PGCMcp phase 1b: an MCP client, to read the protocol from the other side ✅ (2026-10-31)

Context: PGCMcp V1 (the read-only MCP server, 11 tools over the public pgcgolf.club API) was
built and merged in its own repo, `github.com/ouyanone/PGCJavaMcp`, and is tracked there plus in
the superpowers ledger rather than here. This entry starts recording that project in PROGRESS.md.

Reported: the MCP server works in Claude Desktop, but the traffic could not be seen. Claude
Desktop's logs now record only `id=1 result` summaries instead of full JSON bodies, so the
message bodies were genuinely not there to read — not a configuration mistake.

**Why a client and not more logging:** a server only ever answers. Protocol version negotiation,
tool discovery, and server-initiated requests all happen from the client's side, so no amount of
server code or log tailing exposes them.

**Change (PGCMcp only; PGCServer and PGCUI untouched):** a client in
`club.pgcgolf.mcp.client` — `PgcMcpClient` (runs one whole session), `ClientOptions`,
`WireLogTransport` + `WireLog`, `SessionReport`, `ToolArguments`, `SamplingResponder`.

- The traffic is made visible by a **transport decorator**, not by changing the SDK or the
  server. Outbound logs on `sendMessage`; inbound wraps the handler the client hands the
  transport and taps the stream, since the transport calls the client rather than the reverse.
- The client answers `sampling/createMessage` — the one request that runs server-to-client. PGC's
  tools never ask for a completion, so the test suite stands up a stub server whose single tool
  does. The resulting transcript shows a `tools/call` still open while a sampling request is
  answered in the other direction, which is the point.
- `PgcMcpServer.main` was split so `serve(json, tools, about)` can host any tool set over the same
  stdio lifecycle; that is what lets the tests launch the stub server.

**Verification:** 188 tests pass (105 pre-existing + 83 new), `mvn package` exit 0. Round-trip
tests launch the real server as a child process pointed at `http://127.0.0.1:1`, so nothing
touches the network. Also run by hand against production: handshake negotiated protocol
`2025-11-25`, server reported `pgc-golf 1.0.0` with capabilities `logging, tools`, all 11 tools
discovered, `find_player name=Ou` returned 1133 chars. Two tests were proved honest by planting
the defect they claim to catch (a transport that logs but swallows a message; a plural "1 tools").

Note for later: request ids on the wire are strings (`15b90052-0`), not integers — the spec
allows either, and code that assumes a number would break.

Committed on PGCMcp branch `feature-mcp-client-1b` (`e919f1b`). Not pushed, no PR, not deployed.

### STEP47 — the box now survives a reboot ✅ (2026-11-01)

Reported: "can you make tomcat and nginx to start after the box is rebooted? so my website can be
available after reboot?"

**Cause:** worse than described. `nginx` was `systemctl disabled`, and Tomcat had **no systemd
unit at all** — it was started by hand with `bin/startup.sh` as root. Only `mysqld` was enabled.
So a reboot brought back the database and nothing else, and the site stayed down until someone
SSH'd in.

**Change (server only; no application code):**

- `systemctl enable nginx`.
- New `/etc/systemd/system/tomcat.service`: `Type=simple` running `catalina.sh run`,
  `JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto.x86_64`,
  `CATALINA_HOME=/myApp/apache-tomcat-10.1.30`, `TimeoutStartSec=300` for the 66 MB WAR,
  `SuccessExitStatus=143`, `Restart=on-failure`. Runs as **root**, matching how it has always
  run there — `webapps/ROOT`, `work/`, `temp/` and `logs/` are root-owned, so moving to the
  existing `tomcat` user (uid 1001) is a separate, riskier change.
- **`After=mysqld.service` / `Wants=mysqld.service`** — the app connects to
  `jdbc:mysql://localhost:3306/pgc` at startup and Hibernate runs `hbm2ddl.auto=update`, so
  starting before MySQL accepts connections would fail deployment. This is the ordering most
  hand-written Tomcat units get wrong.
- `.claude/skills/pgc-deploy-aws/SKILL.md` updated to drive Tomcat through
  `systemctl stop|start tomcat`. The old `shutdown.sh`/`startup.sh` steps would have stopped the
  service and then launched a Tomcat **outside** systemd, silently removing reboot survival with
  no visible sign. Verification in that skill now targets the apex host and `journalctl`.

**Verification:** switchover performed live. Old hand-started pid 764007 stopped cleanly; new pid
1200534 matches `systemctl show -p MainPID tomcat`, so systemd owns the process. All three of
`nginx`/`tomcat`/`mysqld` report `active` and `enabled`. Public checks: `https://pgcgolf.club/`
200 (18.7 KB, 0.24s), `/webapi/courses` 200, `/webapi/players` 200, `http://` → `https://` 301.
Downtime was one Tomcat restart. A scripted fallback to `startup.sh` was armed and never fired.

**Not verified:** the reboot itself. The units are enabled and the ordering is declared, but only
a real reboot proves the boot path. Use **reboot**, not stop/start — stop/start releases the
public IP unless an Elastic IP is attached. After it comes back:
`systemctl is-active nginx tomcat mysqld` and `curl -o /dev/null -w '%{http_code}' https://pgcgolf.club/`.

**Decision (owner, 2026-11-01):** the TLS certificate covers only `pgcgolf.club`, not
`www.pgcgolf.club`, and that is intentional — the site is for member players and nobody is
directed to the `www` host. Browsers hide the mismatch via cert-name-mismatch fallback; strict
clients (curl, scripts, Anthropic's cloud) fail on it. Not a fault to chase.

### STEP48 — automatic certificate renewal ✅ (2026-11-01)

Found while verifying STEP47, then fixed.

`nginx` serves `/etc/letsencrypt/live/pgcgolf.club-0001/fullchain.pem`, which expires
**2027-01-16**. Nothing on the box renews it: `crond` is **inactive**, `/etc/cron.d` and
`/etc/cron.daily` are empty, `crontab` is not installed, none of the 7 systemd timers reference
certbot, and nothing under `/etc/cron*` or the unit directories does either. certbot itself is a
venv install at `/opt/certbot/bin/certbot` (3.0.0) and is **not on `PATH`**.

When it lapses, `pgcgolf.club` fails for every visitor. No browser fallback helps — an expired
certificate is not a name mismatch Chrome can route around.

`certbot renew --dry-run` findings: `pgcgolf.club-0001` and `www.pgcgolf.club` both renew fine
via the nginx authenticator. `pgcgolf.club.conf` uses the **standalone** authenticator and fails
every time — *"Could not bind TCP port 80"* — because nginx holds port 80. That config is unused
by nginx and is the one that already lists both names; the names were right once, the
authenticator was wrong for a box running nginx. `certbot delete --cert-name pgcgolf.club` would
clear it, but that deletes a certificate and should be a deliberate choice.

**Why a systemd timer and not cron:** cron is not broken on this box — `cronie` was never
installed, there is no `crond.service` unit, and `crontab` is not a command. Amazon Linux 2023
ships without cron and uses systemd timers, seven of which already run here. The timer also has
`Persistent=true`, so a run missed while the instance was down happens on next boot; cron would
silently skip it.

**Change (server only):**

- `/etc/systemd/system/certbot-renew.service` — `Type=oneshot`, `After=nginx.service` (the
  renewal configs use the nginx authenticator, so nginx must be up to answer the HTTP-01
  challenge), `TimeoutStartSec=1800`.
- `/etc/systemd/system/certbot-renew.timer` — `OnCalendar=*-*-* 03,15:00:00`,
  `RandomizedDelaySec=3600`, `Persistent=true`. Enabled, so it survives a reboot.
- `ExecStart=/opt/certbot/bin/certbot renew --no-random-sleep-on-renew --deploy-hook "systemctl reload nginx"`.

**Two flags worth the words.** `--no-random-sleep-on-renew`: certbot otherwise sleeps a random
0–480s on every non-interactive run to spread load on Let's Encrypt, which made a *no-op* run
take **6m49s** and would have pushed a real renewal near any sane start timeout. The timer's own
`RandomizedDelaySec=3600` already spreads load, so the sleep is redundant. And **no `--quiet`**:
this box has no monitoring, so the journal is the only record of what renewal did. Twice a day
is not enough noise to care about.

**Verification:** service run twice. With the sleep, 6m49s; after `--no-random-sleep-on-renew`,
**0s**, `Result=success`, exit 0. The certificate's SHA-256 fingerprint was byte-identical before
and after both runs, proving the plumbing works without touching the certificate. Site served
200 throughout. Timer reports `enabled` and `active`, next run 2026-11-01 15:07 UTC. The journal
now records each decision, e.g. `pgcgolf.club-0001 ... expires on 2027-01-16 (skipped)`.

**Expect a failure around Dec 31, and do not chase the wrong thing.** Three renewal configs
exist, with different expiries: `pgcgolf.club-0001` (2027-01-16, nginx authenticator — this is
the one nginx serves), `www.pgcgolf.club` (2027-01-16, nginx), and `pgcgolf.club` (2027-01-30,
**standalone**). Renewal windows open 30 days before expiry, so the first two renew around
Dec 17 and should succeed. The standalone one comes due around Dec 31 and **will fail** — it
cannot bind port 80 while nginx holds it (confirmed by `certbot renew --dry-run`). certbot then
exits non-zero and `certbot-renew.service` shows as failed even though the certificate that
matters renewed fine. That config is unused by nginx and is, oddly, the one that already lists
both `pgcgolf.club` and `www.pgcgolf.club` — the names were right once, the authenticator was
wrong for a box running nginx. `certbot delete --cert-name pgcgolf.club` would retire it, but
that deletes a certificate and is deliberately left as the owner's decision.

A pre-change backup of the whole nginx config is at `/root/nginx-backup-20261101-032513`.

---

### STEP49 — PGCSpringMCP: three tools over HTTP behind a validated access token ✅ (2026-11-01)

Phase 2 of the MCP work, in a **new sibling repo** `PGCSpringMCP` on branch `feature-phase2-oauth`.
Nine commits, 55 tests. `PGCMcp`, `PGCServer` and `PGCUI` source are untouched — verified, all
three clean.

**What it is.** Spring Boot 4.1.2 + Spring AI 2.0.2 (`spring-ai-starter-mcp-server-webmvc`) + MCP
Java SDK 2.1.0 + Spring Security 7.1.2, on Java 21. Three read-only tools (`list_courses`,
`find_player`, `get_player_profile` — chosen to cover a no-argument, a string and a numeric schema)
served over **stateless** Streamable HTTP at `/mcp`, bound `127.0.0.1:8900`, behind an access token
validated on signature, `iss`, `aud` and `token_use`. Plus a client that performs the whole
discovery chain and the code exchange and prints every message.

**The design rests on one empirical fact, and it is the owner's to confirm.** A Cognito access
token carries **no `aud` claim by default** — it appears only when the authorization request
carried RFC 8707's `resource` parameter. So `docs/cognito-setup.md` is a gate, not a runbook: §1–§6
are the console steps, §7 is where the decoded token goes. **Still PENDING** — it needs the AWS
console and a browser sign-in. The server is written to **fail closed** either way, and that is
what is proved: a token with no `aud` is rejected, and there is no code path that substitutes
`client_id`.

**Verified live**, by driving the packaged jar against the real club API with a locally-signed
token (transcript in `PGCSpringMCP/docs/oauth-wire-log.md`):

- `POST /mcp` with no token → `401` with
  `WWW-Authenticate: Bearer resource_metadata="https://pgcgolf.club/.well-known/oauth-protected-resource/mcp"`.
- Four distinct rejections, each still carrying the challenge: wrong `aud`, absent `aud`, malformed
  token, non-Bearer scheme.
- `initialize` negotiating down from the requested `2026-07-28` to `2025-11-25`.
- `tools/list` with all three schemas generated from the method signatures.
- `list_courses` → 9 real rows, and a search of the whole result for `phone` or `email` finds
  nothing — which matters, because **every** course row carries a `phone` upstream.
- `get_player_profile 999999` → a sentence, not a crash. The club API answers an unknown player id
  with **HTTP 500**, not 404.

**Three things the plan had wrong, found by checking rather than assuming:**

1. **The callback URL.** AWS rejects `http://127.0.0.1` callbacks — its documentation exempts only
   `http://localhost` from the HTTPS requirement. Changed everywhere; the client still *listens* on
   `127.0.0.1`, which is where the name resolves.
2. **The nginx location.** `location /mcp/` does not match `POST /mcp`, which is the exact endpoint.
   Every real request would have fallen through to `location /` and hit Tomcat — a 404 that looks
   exactly like the MCP server being down. It is `location /mcp` with no trailing slash on
   `proxy_pass`, so the path is passed through rather than stripped.
3. **The course field names.** Upstream they are `parNumber`, `rating` and `slop`, not `par`,
   `courseRating` and `slope`. The plan flagged one of the three.

**And one defect in my own code, found only by running it.** The published RFC 9728 document said
`"bearer_methods_supported": ["header", "header"]`. Spring Security's builder already defaults that
list, and the explicit call appended a second entry. No test covered it; `DiscoveryTest` now has one
that RED'd on `[header, header]` before the fix. Two of Spring Security's own defaults were also
wrong here and are now set explicitly: `resource` would have been derived from the request
(`http://127.0.0.1:8900/mcp`), and `tls_client_certificate_bound_access_tokens` defaults to **true**
while this server does no mTLS binding at all.

**Where the owner's authorization logic goes:** `policy/AccessPolicy.java`. Declare one `@Component`
implementing it and the allow-all default is replaced. The token is already proven valid when
`decide` is called, so it decides about the *person*. A policy that throws or returns `null`
**denies**, and the caller is told the policy failed rather than shown its stack trace — a bug in
authorization code must not become a way around it.

**Not deployed.** `docs/deploy.md` is the runbook, with the nginx step last, `nginx -t` before every
reload, and a rollback ordered the way you would want it under pressure. Deploying is an
outward-facing change that waits on the owner and on the `aud` gate. Port 8900 is free, `/usr/bin/java`
is Corretto 21.0.4, and `/etc/nginx/conf.d/` is empty — so the location blocks are a paste-in
fragment for the one `server` block in `nginx.conf`, not a drop-in file.

**Still open:** the Cognito gate (§7); the GitHub remote for `PGCSpringMCP`, which is the owner's to
create; and the deploy itself.

#### STEP49b — the review, and what it caught (2026-11-01)

A fresh reviewer read the whole branch with no memory of building it. Verdict: **ready to merge
with fixes** — 1 Critical, 5 Important, 8 Minor. It found no path by which an unauthenticated or
wrong-audience caller reaches a tool, none by which `phone`/`email` reaches a tool result, and no
secret in git, a log or a committed doc. All 61 tests green after the fix pass.

**The blocker was embarrassing and important.** Every one of the four places that documents how to
run the client said:

```
java -cp target/pgc-spring-mcp.jar club.pgcgolf.springmcp.client.PgcHttpMcpClient ...
```

That cannot work. `spring-boot-maven-plugin` repackages application classes under
`BOOT-INF/classes/` and dependencies under `BOOT-INF/lib/`, so a plain `-cp` finds neither:
`ClassNotFoundException`, on the owner's first attempt, at the project's headline deliverable. The
reason it survived is worth remembering — the one real end-to-end run was driven with `curl`, so
the documented path was never the path exercised. It now reads:

```
java -Dloader.main=club.pgcgolf.springmcp.client.PgcHttpMcpClient \
     -cp target/pgc-spring-mcp.jar \
     org.springframework.boot.loader.launch.PropertiesLauncher ...
```

And this time it was actually run — which paid for itself immediately by capturing, against a
**live** Cognito pool, the one hop the unit tests can only stub: `/.well-known/oauth-authorization-server`
answers **400**, then `/.well-known/openid-configuration` answers **200** with the real authorize,
token and JWKS endpoints. RFC 8414 is the specification for exactly that lookup and Cognito does
not implement it; a client that stopped there would never find a token endpoint. That is now
`docs/oauth-wire-log.md` §2b, and flow step 3 moved from PENDING to real.

**Two tests were passing against broken code.** `ApplicationContextTest` asserted the `/mcp`
response was "not 404" — which stopped distinguishing anything the moment the security filter chain
arrived, because the filter answers 401 before the dispatcher decides whether a handler exists. The
honesty check done when that test was written was genuine *then*, and silently expired two tasks
later. Worse, nothing in the suite exercised the `SecurityContext` on the thread Spring AI actually
invokes a tool from: had an upgrade moved tool execution off the request thread, every call would
have returned *"no validated access token"* with all tests still green — failing closed, but
silently. A new `AuthenticatedToolCallTest` does a real JSON-RPC `tools/call` with a valid token
against a stubbed club API; both fixes were verified by planting the defect and watching it fail.

**Four real defects, each re-graded upward by what it costs a person:**

- **`PgcApi` treated a 3xx as success**, deserialising an empty body into an empty list — an error
  arriving as an empty result, the one thing this project says it never does, and the thing a model
  reports as "there are no courses". A redirect is now a failure, and the client no longer follows
  one, so the read-only choke point cannot be escaped through a `Location` header.
- **`get_player_profile` forwarded every upstream field** minus `phone`/`email`. It now allowlists
  like its two siblings, so a field the club API gains next year does not reach a model by default.
- **`OAuthFlow` accepted a redirect with no `state`** — authorization-code injection, the exact
  attack `state` exists to prevent. Required now on the listener path.
- **`ToolAccessGuard` caught `Exception` but not `StackOverflowError`.** Accidental recursion is the
  likeliest shape of a bug in a hand-written policy, and it was the one policy bug the guard built
  to contain policy bugs did not contain.

Plus `docs/cognito-setup.md` §7 had exported the issuer but **not** the JWKS URI — which would have
rejected every token at precisely the moment the owner was confirming the gate passed, with a 401
and an apparently-correct issuer. That was the cost the Task-5 decision had itself named, and then
the doc incurred it.

**Five minors deliberately left**, for the owner to call: the `/admin/` guard is case-sensitive
while its comment implies otherwise (unreachable today — all three tools build paths from constants
plus a `long`); one test is misnamed `aDenyBecomesIsErrorTextInTheToolResult` when a deny is text,
not `isError`; §6.4 puts the client secret in `curl`'s argv, visible in `ps`; `location /mcp` is a
prefix so `/mcpanything` also proxies (and gets 401); and a body-parse failure is reported as
"could not reach".

---

### STEP50 — the Cognito gate settled, and PGCSpringMCP deployed ✅ (2026-11-05/06)

Phase 2 had one assumption no test could prove: that Cognito would mint an access token whose
`aud` is the resource identifier `https://pgcgolf.club/mcp`. The server was written to **fail
closed** — a token with no `aud`, or an `aud` naming anything else, gets a 401 — so if Cognito
would not honour the RFC 8707 `resource` parameter, the design was wrong rather than the code.

**It honours it.** Pool `us-east-1_kfC3n4qy7` returned `aud: "https://pgcgolf.club/mcp"`,
`token_use: "access"`, `scope: "https://pgcgolf.club/mcp/tools.read"`. Claims are recorded in
`PGCSpringMCP/docs/cognito-setup.md` §7, which is now VERIFIED rather than PENDING; no token was
written to any tracked file.

**Three console findings, each of which cost real time and none of which is in the AWS walkthroughs:**

- A prefix domain is lowercase; a pool id is case-sensitive. `us-east-1kfc3n4qy7` does not tell you
  the pool id, which is `us-east-1_kfC3n4qy7`. Deriving it gives a 404 on discovery and then a
  blanket 401 with nothing in the log naming the cause. Found by enumerating case variants against
  the discovery endpoint.
- **Creating a resource server grants its scopes to no client.** Each app client must tick the
  custom scope separately. Until it does, `/oauth2/authorize` answers `invalid_scope` — identically
  to a scope that does not exist, so the error cannot distinguish "not created" from "not granted".
  Proved by sending six spelling variants plus a nonsense control: all nine behaved the same.
- `username` holds the `sub` UUID when no alias attributes are configured. `pgc.mcp.login-claim=username`
  is correct but not human-readable, so an `AccessPolicy` keyed on names needs an alias attribute or
  its own mapping from `sub`.

**Deployed to the EC2 box** per `docs/deploy.md`: jar at `/opt/pgc-mcp`, config in
`/etc/pgc-mcp.env` (0600, not in git), `pgc-mcp.service` active **and enabled**, JVM on
`127.0.0.1:8900`, two `location` blocks added to the 443 server with a backup at
`/root/nginx.conf.before-mcp-20261106-025611` and `nginx -t` gating the reload.

Verified from the public internet: website 200, `POST /mcp` unauthenticated 401 with a
`WWW-Authenticate` naming the RFC 9728 document, that document served publicly with the right
resource and issuer, and a real Cognito token driving `initialize` (protocol 2025-11-25),
`tools/list` and `tools/call` against live club data with `phone` and `email` absent.

**The reboot claim is no longer a claim.** The box was rebooted after 31 days of uptime; the site
answered 200 after 17 seconds and `nginx`, `tomcat`, `mysqld`, `pgc-mcp` and `certbot-renew.timer`
all came back active with no intervention.

**The deploy also found the root filesystem at 99% — 159 MB free on 8 GB**, with a 36 MB jar to
place. Causes: the systemd journal at 780 MB, Tomcat's `catalina.out` at 261 MB with no rotation,
a stale 200 MB `corretto.rpm`, and 75 MB of dnf cache. Reclaimed all four (84%), then deleted 626
Tomcat log files dated 2024-09-20 to 2026-10-31, taking that directory from 49 MB to 1.0 MB.
`deploy/logrotate-tomcat-catalina` fixes the cause with `copytruncate` — a plain rotate would leave
the new file empty until Tomcat restarted, and restarting Tomcat takes the website down.

**Left open:** installing that logrotate drop-in (the sandbox denied the remote write, so it is the
owner's to run); Tomcat's `localhost_access_log.*` still accumulate forever and want
`maxDays` on the `AccessLogValve`; `www.pgcgolf.club` still fails TLS because the served
certificate omits that name, which is the `www` certificate the owner declined and is unrelated to
this deploy.

#### STEP50b — a third-party MCP client, over OAuth, end to end (2026-11-06)

Claude Desktop connected to `https://pgcgolf.club/mcp` as a custom connector and ran tools. This is
the first time the server was driven by a client this project did not write.

Getting there took two Cognito edits. Claude Code needed none beyond the client id: it accepts a
pre-registered `--client-id`, a `--client-secret` and a `--callback-port`, and its redirect URI is
`http://localhost:${port}/callback`, which was already registered. **That answers the question the
missing `registration_endpoint` raised** — Cognito advertises no RFC 7591 dynamic client
registration, and a client that cannot be told a static client id simply cannot talk to a
Cognito-protected MCP server. Claude Code can be told. Claude Desktop needed a `claude.ai` callback
URL added to the app client.

**The server's own log is the evidence, and it carries two findings worth more than the success:**

```
Info: Implementation[name=Anthropic/Toolbox,  version=1.0.0]
Info: Implementation[name=Anthropic/ClaudeAI, version=1.0.0]
```

1. **The traffic originates from Anthropic's infrastructure, not the laptop** — `160.79.106.x`,
   user-agent `Claude-User`. A hosted custom connector runs both the OAuth flow and the MCP calls
   server-side, which is why its redirect URI is a `claude.ai` URL and why nothing appeared in the
   desktop app's local logs. The access token therefore lives at Anthropic rather than on the
   user's machine. A local client such as Claude Code keeps it on the machine. Same protocol,
   materially different data path — the distinction that decides whether this pattern is acceptable
   behind a corporate IdP.
2. **Consecutive calls in one session arrived from different source IPs** (`.17`, `.23`, `.188`,
   then `.140`). The stateless choice — `spring.ai.mcp.server.protocol=STATELESS`, "any request may
   land anywhere" — is what makes that work. A session-affinity server would have failed there
   intermittently, with a cause nearly undiagnosable from the server side.

Every request returned 200 or 202; no 401s, so the claude.ai client sent the RFC 8707 `resource`
parameter and the token's `aud` matched `pgc.mcp.resource-id`. Phase 2's goal — an OAuth access
token reaching an MCP server and being validated there — is met with a real client against real
infrastructure.

### STEP51 — PGCSpringMCP: a fourth tool, `list_players` ✅ deployed (2026-11-10)

**What was asked:** expose `GET /webapi/players?includeInactive=true` as an MCP tool that lists
every player.

**What was built:** `list_players` in `PgcTools` — no arguments, returns the whole roster through a
new allowlist projection, `rosterEntry`: `playerId`, `fName`, `lName`, `nickName`, `pgcHandicap`,
`isActive`. It reuses `playerSummary` and adds the one field that only matters once inactive
players are in the answer.

**Decisions made, and why:**

- **Six fields, not twenty.** The upstream row carries 20 fields including `phone` and `email`; the
  raw answer is 43 KB. Projected it is 12 KB for 127 players. This is the largest result the server
  returns, so each extra field is paid for 127 times in the caller's context window. Detail stays in
  `get_player_profile`, which was checked to answer for inactive ids too.
- **No argument.** The query string is a constant in the code; nothing a model sends reaches the
  URL. An `includeInactive` flag was considered and left out — every row says `isActive`, so the
  model can filter, and one fewer argument is one fewer thing to get wrong.
- **The description steers away from it.** It tells the model to use `find_player` for a name and
  this tool only for whole-roster questions, because a model that calls the big tool to look up one
  person has spent 12 KB to do what 200 bytes would.
- **No row cap, deliberately, and written down.** Row width is fixed by the code but row count is
  set by club membership. `docs/why-large-responses-are-a-failure.md` now says so and names the
  remedy if the roster reaches the thousands.

**Found along the way:** `find_player` reads the bare `/webapi/players`, which returns the 109
active players only. So `list_players` is the only tool that can surface the 18 inactive ones, and
a name search for a former member finds nothing. Not changed — it was not asked for — but recorded
in the README.

**Verification:** tests first, watched fail on the missing method, then 67/67 green (61 before).
Six new tests: both kinds of player returned and flagged, the exact path requested, phone/email
never present, unknown upstream fields left out, an empty roster answered in words, an upstream 500
explained; plus one in `PgcApiTest` pinning that a query string reaches the wire unencoded, which
the stub-based tool tests cannot see. Then the built jar was run locally against the live club API
with a real Cognito token: `tools/list` shows four tools, `list_players` returned 127 rows
(109 active, 18 inactive), 12,393 bytes, keys exactly the six above, no `@` anywhere in the answer.

**Committed and deployed:** PGCSpringMCP `0daf9ea` on `feature-phase2-oauth` (not pushed). The box
had 1.3 GB free (85%) beforehand. The jar went up as `.new`, its SHA-256 matched the local build,
the running jar was copied to `/opt/pgc-mcp/pgc-spring-mcp.jar.prev`, then swap and
`systemctl restart pgc-mcp` -- back answering 401 on loopback in 6 seconds. No nginx or Tomcat
change; `tomcat`, `nginx` and `mysqld` stayed active throughout.

Verified from outside with a real token: site 200, `/mcp` 401 without a token, `tools/list` shows
four tools, `list_players` returned 127 rows (109 active, 18 inactive), 12,393 bytes, the six
allowlisted keys and no `@`; `list_courses` still answers.

**Rollback, if ever needed:**
`cd /opt/pgc-mcp && cp -p pgc-spring-mcp.jar.prev pgc-spring-mcp.jar && sudo systemctl restart pgc-mcp`.
The `.prev` file is 36 MB and can be deleted once the new build has been in use for a while.

---

### STEP52 — PGCSpringMCP publishes the authorization server's metadata itself ✅ deployed (2026-11-10)

**What was asked:** MCPJam would not connect to the deployed server. It failed three times, on
three different rules, and after the third the question was whether to make the server answer for
Cognito. Answer: yes — "but make a backup before making any change".

**What the three failures were:**

| MCPJam said | Cause |
|---|---|
| no `registration_endpoint` | Cognito has no dynamic client registration. Settled on the client: a pre-registered client id |
| PKCE required, `code_challenge_methods_supported` not advertised | Cognito accepts S256 and never says so; the 2025-11-25 MCP spec tells a client to refuse |
| could not discover metadata, HTTP 400 | Cognito serves metadata only at `{issuer}/.well-known/openid-configuration`; older protocol versions look only at `/.well-known/oauth-authorization-server` |

The last two cannot be fixed on the client, and no MCPJam setting gets past both. (Advice given
mid-way to pick protocol `2025-06-18` was wrong — it traded the second failure for the third.)

**What was built:** the server now serves `GET /.well-known/oauth-authorization-server/mcp` — the
pool's own discovery document, fetched once from Cognito, with `issuer` set to
`https://pgcgolf.club/mcp` and `code_challenge_methods_supported: ["S256"]` added. The
protected-resource metadata names that issuer instead of Cognito's. Every endpoint in the document
is still Cognito's; no code or token passes through this server; token validation is unchanged.

**Decisions made, and why:**

- **Republish, do not invent.** Nothing in the document is hard-coded except the two changed lines.
  If Cognito's document cannot be read, names a different issuer than the one tokens are validated
  against, or lacks an authorization or token endpoint, the answer is `502` with the reason.
- **The issuer is the server's URL**, because RFC 8414 requires the `issuer` in a document to match
  the URL it was found under, and clients check.
- **Only that one path is opened.** The root form and `/.well-known/openid-configuration` stay
  denied; `SecurityConfig` is still deny-by-default.
- **Fetched once; a failure remembered for ten seconds.** The endpoint is open to anyone, so it must
  not become a way to make this server call Cognito per request, or queue on timeouts in an outage.
- **Known cost, written down:** the advertised issuer is no longer the `iss` in the token. An MCP
  client never compares them. An OIDC client validating an ID token would. `oauth-wire-log.md` §2c.
- **The project's own client was wrong too.** `DiscoveryChain` appended the well-known segment to
  the issuer path instead of inserting it before; it only ever worked against root issuers and
  Cognito's fallback. It now tries the spec's three URLs in the spec's order.

**Backups, taken before anything changed and checksum-verified:**
git tag `backup/pre-as-metadata-20261110` (at `0daf9ea`);
`/opt/pgc-mcp/pgc-spring-mcp.jar.bak-20261110-pre-as-metadata`;
`/etc/nginx/nginx.conf.bak-20261110-pre-as-metadata`.

**Verification:** tests first. 88/88 green (67 before): 13 unit tests for the document, 6 more in
`DiscoveryTest`, 2 in `DiscoveryChainTest`. The five `DiscoveryTest` additions were confirmed to
fail with the security wiring removed. The packaged jar was then run locally against the live pool
and returned the real document before anything was deployed.

**Deployed:** PGCSpringMCP `445ab9e` on `feature-phase2-oauth` (not pushed). nginx first — one
additive `location /.well-known/oauth-authorization-server` block, `nginx -t` passed, reload —
then the jar: uploaded as `.new`, SHA-256 matched, swapped, `systemctl restart pgc-mcp`, answering
in 6 seconds. `tomcat` and `nginx` stayed active. Disk 86%, 1.2 GB free.

Verified from outside: site 200, `/webapi/courses` 200, `/mcp` 401 with the same challenge, the
resource metadata lists `https://pgcgolf.club/mcp`, the new document answers 200 with that issuer,
Cognito's endpoints and S256, and the project's own client resolves it on the first request.

**Not verified:** a tool call with a real token against the new jar (the last token had expired and
a new one needs a browser sign-in), MCPJam's Connect itself, and Claude Desktop.

**Still needed on the Cognito side:** MCPJam's callback URLs are not on the test app client's
allow-list (only `http://localhost:8765/callback` is), so the login will stop at
`redirect_mismatch` until they are added.

**Rollback:**
`sudo cp /etc/nginx/nginx.conf.bak-20261110-pre-as-metadata /etc/nginx/nginx.conf && sudo nginx -t && sudo systemctl reload nginx`
and
`cd /opt/pgc-mcp && cp -p pgc-spring-mcp.jar.bak-20261110-pre-as-metadata pgc-spring-mcp.jar && sudo systemctl restart pgc-mcp`.
Do both or neither: the old jar with the new route is harmless, the new jar without the route
sends clients to a 404.

---

### STEP53 — PGCPyMcp: the PGCSpringMCP server ported to Python ✅ built, not deployed (2026-11-10)

**Asked for:** "lets create another project called PGCPyMcp and implement it with python".

**What it is:** a new sibling project, `pgc-ai/PGCPyMcp`, with the same four read-only tools
(`list_courses`, `list_players`, `find_player`, `get_player_profile`) over stateless Streamable
HTTP behind a validated Cognito access token. Server and tests only: no client, no deployment.
Python 3.12, MCP Python SDK 2.3 (`MCPServer`), PyJWT, httpx, uvicorn, managed with `uv`. It
listens on `127.0.0.1:8901` so it can run beside the Java server on `8900`.

**How it is built:** on the SDK's own authentication path rather than around it. The SDK takes
the bearer token, issues the `401` challenge, serves the RFC 9728 document and enforces the
scope; this project supplies the `TokenVerifier` (RS256 signature against the pool's key set,
`iss`, `exp`, `token_use=access`, `aud` contains the resource id, never falling back to
`client_id`), the RFC 8414 document republished from Cognito with the issuer rewritten and S256
added, the `/webapi/`-only GET choke point, unconditional `phone`/`email` stripping, and the
access-policy seam. Tool names, argument names, descriptions and answers match the Java server
word for word.

**Decisions made without asking:** `serverInfo.name` is `pgc-golf-py` so the two servers can be
told apart; the bind address is a constant, not a setting; the project is not installed as a
package (on this Mac the virtualenv's `.pth` file carries the hidden flag and Python 3.12 skips
it), so commands run from the project directory.

**Differences from the Java server a client could notice:** the `tools.read` scope is enforced
(`403`), not just advertised; no clock-skew leeway on `exp`; unknown paths answer `404`, not
`401`; the challenge carries `error="invalid_token"` even with no token; the challenge URL is
derived from the resource id; `Host` must be a loopback name or the public host (`421`
otherwise, checked after authentication). All listed in the project's README.

**Verification:** tests first throughout. 197/197 green, no network. The security checks were
each removed in turn to confirm a test fails: the audience check, `token_use`, the issuer, the
key-set refetch cooldown, redirect-following, the policy deny, the `Host` allow-list, the scope
requirement and the verifier's audience wiring all did. (Removing the up-front `alg` header
check alone fails nothing, because the decode call pins RS256 as well.) Then run on a real
socket against the throwaway test pool: `/mcp` without a token `401` with the challenge, both
open documents `200` with Cognito's real endpoints and S256, a garbage token refused and logged,
listening on loopback only.

**Not verified:** a tool call with a real Cognito token; any MCP client connecting; running
behind nginx.

**State:** nothing committed (the repository has no commits yet), nothing pushed, nothing
deployed, production untouched.

### STEP54 — PGCSpringMCP: eight more tools, twelve in all ✅ deployed (2026-11-10)

**Asked for:** "add more tools to the mcp spring mcp server, such as get player score history,
get standings etc."

**Built:** the Spring server now covers the same ground as the stdio PGCMcp server, plus
`list_players`. Eight tools added, each test-first:

| Tool | Reads | Class |
|---|---|---|
| `get_player_score_history` | `/webapi/players/{id}/score-history` | `PgcTools` |
| `get_standings` | `/webapi/points/standings` | `ClubTools` (new) |
| `get_club_news` | `/webapi/news` | `ClubTools` |
| `list_events` | `/webapi/events` | `CompetitionTools` (new) |
| `get_event_scores` | `/webapi/points/game-scores/event/{id}` | `CompetitionTools` |
| `get_event_rewards` | the same document's `rewards` | `CompetitionTools` |
| `list_tournaments` | `/webapi/tournaments` | `CompetitionTools` |
| `get_tournament_totals` | `/webapi/points/game-scores/tournament/{id}` | `CompetitionTools` |

`ToolSupport` (new) holds what the three tool classes share: JSON, limits, the failure
sentences. All three are registered in `PgcSpringMcpApplication.pgcToolCallbacks`, so the access
policy wraps the new tools without further change. Every path is still a constant or a constant
plus a numeric id, GET only, under `/webapi/`, nothing under `/admin/`.

**Decisions made without asking** (where the Spring tools differ from PGCMcp's):
- a season is matched by part of its name (`2025` finds `PGC 2025`); an unknown one gets a
  sentence listing the seasons that exist;
- `get_player_score_history` returns the current season by default, says so in `scope`, and
  keeps the whole-history figures apart under `allSeasons`;
- upstream's `front9`/`back9`/`hole1..18` on an event score are strokes relative to par, so they
  leave as `front9ToPar`/`back9ToPar`/`holesToPar`;
- `get_event_rewards` reads the rewards embedded in the event-scores document, never
  `/webapi/rewards?eventId=`, which nests each winner's whole player row;
- `list_tournaments` takes its dates from the rounds, because the hand-typed `endDate` is wrong
  on live data;
- `get_tournament_totals` lists players who missed a round after those who played them all
  (`complete:false`), omits `totalNet` where a round has no net, and flags a name that appears
  twice in one round (`ambiguousName`) since scores carry no player id;
- news posts with `isActive:false` are left out;
- lists that are cut off (`get_standings` 20, `get_club_news` 5, `list_events` 10) answer with an
  object carrying the full count and a `note`.

**Verification:** 153/153 green (88 before), no network. Ten mutations each failed a test and
were reverted: withdrawn news kept, whole standings row passed through, truncation note dropped,
dense ranking, event 404 reported as a server fault, holes always included, nested course passed
through, upstream `endDate` trusted, partial totals ranked on gross alone, missing net counted
as zero. Then each new tool was called once against the live club API from a throwaway test
(deleted afterwards): all returned the expected shape with no `phone`/`email`; default answers
ran from 0.4 KB (`list_tournaments`) to 4.3 KB (`get_event_scores`, 26 players), and the whole
109-row standings table is 11.6 KB.

**Not verified:** the new tools through the running server with a real Cognito token; any MCP
client (MCPJam, Claude Desktop) listing twelve tools.

**Docs:** README (tool tables, counts, layout), `docs/why-large-responses-are-a-failure.md`,
and the comments that named a tool count. `docs/oauth-wire-log.md` and `docs/cognito-setup.md`
still say "three tools" where they record what was captured at the time; left as records.

**State:** committed on `feature-phase2-oauth` as `03ccfa1`, pushed, and added to the open
pull request (`ouyanone/PGCSpringMcp` #1, description updated to cover it); **deployed**
2026-11-10 21:14 EST (02:14 UTC on the 11th). The first attempt was refused by the session's
permission mode; the owner then asked for the deploy directly. Jar built from `03ccfa1` on
Java 21 (sha256 `d5d4dc4f…c3beb9`, verified on the server before the swap). Backup taken first:
`/opt/pgc-mcp/pgc-spring-mcp.jar.bak-20261110-pre-twelve-tools` (sha256 `ac8b8b23…2037de`, the
four-tool jar). Only `pgc-mcp` was restarted; Tomcat and nginx were not touched.

**After the deploy:** the service log says `Registered tools: 12`, started in 3.7 s;
`pgc-mcp`, `tomcat` and `nginx` all active; `POST /mcp` without a token answers `401` with the
challenge naming `https://pgcgolf.club/...`; the site answers `200`; both metadata documents
answer, the second with the `/mcp` issuer and `S256`.

**Rollback:** `cd /opt/pgc-mcp && cp -p pgc-spring-mcp.jar.bak-20261110-pre-twelve-tools
pgc-spring-mcp.jar && sudo systemctl restart pgc-mcp`.

**Still not verified:** a tool call with a real Cognito token against the deployed server; an
MCP client listing twelve tools. PGCMcp and PGCPyMcp untouched.

### STEP55 — A learning plan for LangChain and LangGraph 📝 (2026-11-10)

**Asked for:** a plan for learning LangChain and LangGraph, using PGC Golf as the sample, saved as
a Markdown file for future reference.

**Written:** `PGCLangLab/LEARNING-PLAN.md` (new folder, nothing else in it yet). Eight stages, from
setup through structured output, tools and `create_agent` over the PGC MCP tools, a hand-built
LangGraph agent, memory, an event-recap workflow with human approval, evaluation, and a capstone
passing a member's Cognito token through to `https://pgcgolf.club/mcp`. Python chosen over Java
because LangGraph is Python-first.

**State:** not a git repository, nothing committed. No code written; Stage 0 not started.

### STEP56 — Chatbot design written 📝 (2026-11-15)

**Asked for:** build a chatbot for pgcgolf.club. Brainstormed first; nothing built.

**Owner decided:**
- logged-in members only;
- club questions answered from the MCP tools, general golf kept short, everything else declined;
- any player's details may be asked about;
- "me" resolves to the member's own player record;
- replies in the question's language (Chinese included);
- the last 3 exchanges are kept;
- $10/month budget;
- placement option C (a 💬 icon in the top bar opens a right-side panel);
- the member's own Cognito token reaches the MCP server;
- the MCP server moves from the test pool (`us-east-1_kfC3n4qy7`) to "pgc new pool"
  (`us-east-1_XDeOxg5Ea`) through additive pool changes.

**Written:** `docs/superpowers/specs/2026-11-15-pgc-chatbot-design.md`, awaiting the owner's
approval. Not committed.

**Found:** the MCP server (`/etc/pgc-mcp.env`) trusts the test pool, so members' tokens would be
rejected today. Local development logs in through the production pool and app client, so the
pool additions must come before local testing.

**State:**
- no code;
- no Cognito, MCP server or database changes;
- the Anthropic API account is still to be created by the owner.

### STEP57 — MCP server moved to "pgc new pool" 🔁 (2026-11-15)

**Asked for:** chatbot step 0. Switch the MCP server from pgcspringpool to "pgc new pool" and
prove it with MCPJam before writing any chatbot code. The design's rollout section was updated
to match.

**Owner, in the AWS console (additive):**
- added resource server `https://pgcgolf.club/mcp` with scope `tools.read` to "pgc new pool";
- created a new app client, `mcpjam-test` (`6ct6edprh3krut3n3oc4n4s7ki`), with that scope;
- left the website's app client untouched.

**Pre-check, before the switch, with no sign-in:** the authorize request with the real scope and
`resource=` reached the sign-in page. A made-up callback gave `redirect_mismatch`, and a made-up
scope gave `invalid_scope`. So the client, scope and callback (`http://localhost:6274/oauth/callback`)
are set up correctly.

**Switch:**
- backed up to `/etc/pgc-mcp.env.bak-20261115-pre-new-pool` and checked the copy with `cmp`;
- changed the issuer and JWKS from `us-east-1_kfC3n4qy7` to `us-east-1_XDeOxg5Ea` (two lines);
- restarted `pgc-mcp`; `/mcp` answered 401 after 6 seconds;
- tomcat and nginx stayed active.

**Verified from outside:**
- the site returns 200;
- `/mcp` returns 401 with the resource-metadata challenge;
- the authorization-server metadata now names the "pgc new pool" endpoints, with S256.

**Rollback:**
`sudo cp -p /etc/pgc-mcp.env.bak-20261115-pre-new-pool /etc/pgc-mcp.env && sudo systemctl restart pgc-mcp`.

**Verified by the owner in MCPJam:** works end to end against the live server.
- Settings: protocol 2025-11-25, a pre-registered client, the scope set explicitly, and a sign-in
  with the owner's own pgcgolf.club account.
- The server rejects any token without `aud = https://pgcgolf.club/mcp`, so a working tool call
  means "pgc new pool" honours `resource=` (RFC 8707), as the test pool did.
- One extra console step was needed: the first sign-in showed "Login pages unavailable" until a
  managed-login style was created for the `mcpjam-test` app client (Branding → Managed login).
  New app clients in this pool need one.

### STEP58 — Club assistant built (plan Tasks 1–8) 🛠 (2026-11-16)

**Asked for:** build the chatbot from the approved design and plan
(`docs/superpowers/plans/2026-11-15-pgc-chatbot.md`), inline, with one fresh review at the end.

**Built (PGCServer `85ba9cb..ca8ae2e`, 7 commits; PGCUI `8d3f9e96`):**
- the website login asks for `https://pgcgolf.club/mcp/tools.read` and sends `resource=`;
- the member's access token is taken from the session and refreshed through
  `OAuth2AuthorizedClientManager`;
- three tables (`ddl/chat.sql`): last 3 exchanges, daily counts, monthly spend in DECIMAL;
- "me" is resolved by login email → exactly one Player;
- a hand-written MCP client sends the member's bearer token on every request. The SDK needs newer
  Reactor and Jackson than Boot 3.3; the owner declined Boot 4 because production runs
  Tomcat 10.1.30;
- the Claude tool loop: Anthropic Java SDK 3.2.0, `claude-sonnet-5-5`, effort low,
  `max_tokens` 2048, at most 5 tool calls; thinking blocks are replayed unchanged;
- `GET /webapi/chat/history` and `POST /webapi/chat` (JSON only, members only);
- a toolbar icon and slide-in panel in Angular, with answers rendered through the sanitizer.

**Tests:**
- backend 140/140 on JDK 21. The default `mvn` uses JDK 25, where Mockito cannot mock classes;
  `./mvnw` is broken because `.mvn/wrapper` is missing;
- UI 19/19 targeted specs. The other 27 generated UI specs already failed before this work;
- mutation checks on the SQL (zero floor, member scope, decimal add), the startup price check,
  and the XSS sanitizer.

**Local DB:** `chat.sql` applied to local MySQL 9.7 through JDBC; there is no `mysql` CLI.

**Not done yet:**
- Task 9: the owner ticks `tools.read` on the website app client, provides an API key, and
  tests end to end;
- Task 10: production rollout;
- the final review is done:
  - verdict: with fixes; no critical issues;
  - 4 important issues fixed: a 35 s question deadline, tokens stored in the HTTP session,
    the signin cause logged, and Enter during Chinese input no longer sending;
  - a precondition added to the Task 10 rollout;
  - 10 minor issues deferred, listed in the ledger;
  - suite 144/144, UI specs 21/21.

**Agreed follow-up:** learn Spring AI in PGCServer after a Boot 3.5 upgrade.

### STEP59 — Club assistant tested end to end locally ✅ (2026-11-16)

**Owner ran plan Task 9:**
- the six test questions behaved as designed: own standings, another player, Chinese,
  a short general-golf answer, off-topic declined, history kept to 3;
- after more than an hour signed in, the next question still answered, so the refresh token
  works and the refreshed token keeps `aud`;
- the backend log lines carry no tokens, emails or question text.

**Measured locally:** 3 questions cost $0.042912 (19,006 input / 490 output tokens), about
1.4 ¢ per question, or roughly 700 questions a month for $10. Input dominates: the 12 tool
schemas and tool results go with every step.

**Snag:** the owner's `spring-boot:run` failed on duplicate classes (`… 2.class`) in `target/`,
caused by concurrent builds and/or iCloud Documents sync. `mvn clean` fixed it.

### STEP60 — Club assistant deployed to production 🚀 (2026-11-16)

**Asked for:** plan Task 10, with the owner's "go" for the tables, the build and the deploy.

**Done, in order:**
1. **Precondition:** the website app client allows `tools.read`, shown by the Task 9 local sign-in
   through the same client.
2. **Database backup:** `db-dump/pgc-20261116-002909-server-backup.sql.gz`, 13 tables, trailer
   present.
3. **API key:** the owner created `pgcgolf-chatbot-prod` ("Somewhere else", a plain key) and
   wrote `/etc/pgc/chatbot.properties` on the server, so the key never passed through chat. It
   was created world-readable; tightened to `0600 root:root`, and Tomcat runs as root.
4. **Tables:** `ddl/chat.sql` applied on production MySQL 8.4.3, taking the schema from 13 to
   16 tables, all three new ones empty.
5. **Build:** UI `6eed4048` → `static/`; WAR built on JDK 21 with `mvn clean package`. It is
   108 MB (was about 66 MB) because `anthropic-java-core` alone is 37 MB; it holds one main
   bundle. SHA-256 starts `4b66141799215b9c`.
6. **Deploy:** Tomcat stopped; the old WAR archived as `ROOT.war.11162026_0037`; the new WAR in
   place; Tomcat started and deployed it in 11.9 s, with no SEVERE or ERROR lines.

**Verified from outside:**
- `/` and `/webapi/news` return 200;
- `/webapi/auth/status` shows logged out;
- logged out, `/webapi/chat` and `/webapi/chat/history` return 401;
- `/mcp` without a token returns 401;
- the login redirect carries `tools.read` and `resource=`;
- the new bundle is served;
- tomcat, nginx and pgc-mcp are all active;
- the disk has 954 MB free (89% used).

**Rollback:**
`sudo systemctl stop tomcat; sudo mv $T/webapps/ROOT.war /tmp/ROOT.war.chatbot; sudo rm -rf $T/webapps/ROOT;`
`sudo mv $T/webapps/ROOT.war.11162026_0037 $T/webapps/ROOT.war; sudo systemctl start tomcat`
(`T=/myApp/apache-tomcat-10.1.30`). The tables can stay, since the old WAR ignores them; to
remove them, `DROP TABLE chat_exchange, chat_daily_count, chat_monthly_spend;`. Delete
`/etc/pgc/chatbot.properties` to remove the key.

**Noticed, not acted on:** `webapps/` holds about 50 archived WARs (roughly 3.4 GB), most of the
disk.

**Verified in production by the owner:** the chatbot works. The database recorded 10
questions, all `outcome=answered`, for one member; 3 history rows; spend 2026-11 = $0.217832
(87,706 input / 4,242 output tokens). That is about 2.2 ¢ a question, or roughly 450 questions a
month for $10.

### STEP61 — Committed, pushed, pull requests opened 📬 (2026-11-16)

**Asked for:** "commit and push and open pr".

- PGCServer: `75bf16e` (deployed `static/` bundle, STEP59–60, plan ledger). `feature-07182026` was
  pushed and is level with origin. PR #3 into `main` covers 25 commits (STEP47–61; STEP40–46 were merged earlier by PR #2), with the
  chatbot as the headline.
- PGCUI: `feature-07182026` was pushed (`b5338215..6eed4048`). PR #2 into `main` covers 15
  commits. The two tracked `dist/` files changed by the deploy build were left uncommitted on
  purpose, because the deploy builds `dist/` fresh.
- Both PRs say the branch is already in production, so merging records reality.

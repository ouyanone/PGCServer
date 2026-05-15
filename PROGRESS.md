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

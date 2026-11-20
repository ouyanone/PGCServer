# MCP admin tools — design

Date: 2026-11-18. Owner: Yan Ou. Status: draft for owner approval.

## Intent

Club admins set up the club's 4–5 tournaments a year by telling the club assistant what they want
instead of clicking through the admin screens: "create the Spring Classic, May 3–4, two rounds at
Pine Hills, add these players, group them best-with-worst by PGC handicap, first tee 7:30".
The assistant does it by calling new **write tools** on the club's MCP server, which call new
token-authenticated admin APIs on the website's server.

It is for production use *and* for learning: how write tools, scopes and per-tool authorization
work in MCP. Choices below favour the clear, standard pattern over the shortest path.

## How a tournament works today (from the owner and the code)

- A tournament (`Tournament`: name, description, season, start/end date) has, normally, two
  events: one round per day over two days (`Event`: name, description, date, course, season,
  tournament).
- Before a round an admin adds players to the event's roster (`EventPlayer`) and the app **groups
  them automatically** into tees of up to four (`Tee`: name "Tee N", tee time as a string), using
  one of six strategies in `GroupingService.Strategy`: `GHIN_BEST_TO_WORST_BY_GENDER`,
  `GHIN_BEST_TO_WORST` (default), `PGC_BEST_TO_WORST_BY_GENDER`, `PGC_BEST_TO_WORST`,
  `GHIN_BEST_WITH_WORST`, `PGC_BEST_WITH_WORST`.
- Grouping (`POST /webapi/admin/gameplan/{eventId}/generate`) **first deletes every tee and every
  player score of the event**, then rebuilds them.
- A player added late is put into the first tee with fewer than four players, or a new tee when
  all are full (`POST /webapi/admin/gameplan/{eventId}/players/{playerId}/assign`). Tees are never
  created any other way.
- After the round an admin uploads the scores as an Excel file exported from another app. That
  stays a website task.
- Rewards (`Reward`: name, description, story, group, display order, event, player) record prizes
  such as "closest to the pin" (`POST /webapi/rewards`).

## Goals

1. Nine admin tools on the MCP server, usable from both club bots (the Anthropic-SDK "J" bot and
   the Spring AI "S" bot):

   | Tool | Does | Website equivalent |
   |---|---|---|
   | `admin_create_tournament` | name, start date, end date, season; description optional | `POST /webapi/admin/tournaments` |
   | `admin_create_event` | in a tournament: name, date, course; description optional | `POST /webapi/admin/tournaments/{id}/events` |
   | `admin_add_players_to_event` | adds players to an event's roster; already-present players are skipped | `POST /webapi/admin/gameplan/{eventId}/players` |
   | `admin_generate_groups` | groups the roster into tees with one named strategy | `POST /webapi/admin/gameplan/{eventId}/generate` |
   | `admin_assign_player` | a late player into an open spot, or a new tee | `POST /webapi/admin/gameplan/{eventId}/players/{playerId}/assign` |
   | `admin_set_tee_time` | a tee's time, and optionally its name | `PUT /webapi/admin/gameplan/tee/{teeId}` |
   | `admin_create_reward` | a reward for a player in an event | `POST /webapi/rewards` |
   | `admin_add_player` | a new player: first/last name, nickname, Chinese nickname, GHIN number, gender, handicap, active | `POST /webapi/admin/players` (no id) |
   | `admin_update_player` | changes only the given fields of those same ones | `POST /webapi/admin/players` (with id; replaces the whole record) |

   Player tools never accept or return phone or email (the read tools strip them
   unconditionally; contact details must not travel through chat history or the model
   provider). PGC handicap, points and score averages stay computed by the app. Added by the
   owner on 2026-11-18 after approval ("add user, modify user" = player records, not Cognito
   accounts).

2. The read tools the admin tools need to resolve names into ids. `list_courses`, `list_players`,
   `find_player`, `list_events`, `list_tournaments` already exist; add **`list_seasons`** and
   **`get_event_groups`** (tees, tee times and players of an event, so "set Tee 3 to 8:15" can find
   the tee id and the bot can report the result of grouping).

3. Only members whose sign-in shows **`profile=admin`** are offered the admin tools by the bots.

4. A token-only API on PGCServer, **`/api/admin/**`**, that the MCP server calls with the member's
   own access token.

## Non-goals (first version)

- Deletes of any kind; moving or removing a single player between tees; courses; handicap
  recalculation; the Excel score upload; editing tournaments or events after creation.
- A confirmation step before each change. The owner chose to add it once everything works.
- Real per-member admin checks on the API (see Decisions D2).
- Changing the website's `/webapi/**` endpoints' behaviour.
- PGCMcp (not modified, as before). The work is in **PGCSpringMCP**, the deployed MCP server
  (`/opt/pgc-mcp/pgc-spring-mcp.jar`, branch `feature-phase2-oauth`).

## Anti-goals (would be a failure even if it works)

- A website screen behaving differently because of this work.
- Grouping wiping an event's recorded scores because the bot was asked to regroup.
- A non-admin member getting the admin tools offered in chat.
- An `/api/admin` call succeeding without a valid access token, or an ID token being accepted as
  one.
- Business logic copied: a fix to grouping on the website must also apply to the bot, and the
  other way round.

## Decisions (owner's unless marked "my call")

**D1. Separate token-only API.** The owner's proposal: PGCServer gets `/api/admin/**`
counterparts of the needed `/webapi/admin/**` endpoints, authenticated only by
`Authorization: Bearer <access token>`. The website's session-based `/webapi/**` stays exactly as
it is.
- Implemented as a second Spring Security filter chain with `securityMatcher("/api/**")`, ordered
  before the existing one: `oauth2ResourceServer().jwt()`, stateless, CSRF off (no cookies),
  every `/api/**` request authenticated, anything not explicitly mapped refused.
- Token checks: signature against the Cognito pool's JWKS, issuer, expiry, `token_use=access`
  (reject ID tokens — the same rule PGCSpringMCP's `SecurityConfig` enforces), and the new write
  scope (D3).
- The old `checkPrincipal(OidcUser)` is **not** used on `/api/**`; a token caller would arrive
  there with a null `OidcUser`, which `checkPrincipal` lets through.

**D2. Any valid write-scoped access token counts as admin on `/api/admin`.** Owner's choice for
simplicity, "till there is a need to change it". Reason it isn't trivial to do better: the admin
marker is the Cognito `profile` attribute, which is in the ID token but **not** in access tokens.
Recorded consequence: a signed-in member who uses their own token directly (e.g. with MCPJam)
can run admin tools. Later options: a Cognito `admin` group (`cognito:groups` is in access
tokens), a pre-token-generation Lambda, or a lookup per call.

**D3. A write scope.** *My call:* `https://pgcgolf.club/mcp/tools.write` on the existing Cognito
resource server, beside `tools.read`. The website's sign-in requests it
(`spring.security.oauth2.client.registration.cognito.scope`) so the member token the bots forward
carries it. Every member's token will carry it — the scope separates read from write *tools*, not
admins from members (D2, D4 do that).
- PGCServer `/api/admin/**` requires it.
- PGCSpringMCP: `/mcp` still requires `tools.read` at the door; the admin tools additionally
  require `tools.write` through the existing **`AccessPolicy`** hook (a bean replacing
  `ALLOW_ALL`): tools named `admin_*` → deny "This tool needs the tools.write permission." when
  the scope is absent; all other tools allowed.
- Cognito changes (add the scope to the resource server, allow it on the website's app client)
  are done by the owner in the AWS console; the design document for PGCSpringMCP's
  `docs/cognito-setup.md` gets the steps.

**D4. Bots offer admin tools only to `profile=admin`.** The bots fetch the tool list from MCP for
each question; for a member whose `OidcUser` profile is not `admin`, tools whose name starts with
`admin_` are removed before the list is sent to the model. Applies to both bots
(`ChatController`/J and `SpringAiChatController`/S). *My call:* the `admin_` name prefix is the
marker, because it is reliable in both MCP clients; MCP tool annotations
(`readOnlyHint: false`) are also set on the write tools where Spring AI supports it, for clients
that read them, but the bots do not depend on them.

**D5. Grouping refuses once scores exist.** Owner-approved guard. `admin_generate_groups` (via
`/api/admin`) returns a refusal, without deleting anything, when any player score of the event has
a recorded score. *My call:* "recorded" = `score > 0`, since grouping creates rows with
`score = 0`. The website's own generate button keeps today's behaviour (non-goal: no website
change).

**D6. Shared logic, not copies.** Where the logic of an endpoint lives in the controller today
(grouping, add players, assign player, tee update, tournament event creation), it moves into a
service method that both the `/webapi/admin` controller and the new `/api/admin` controller call.
The website's responses and behaviour must be unchanged; existing tests (and new ones where there
are none) prove it.

**D7. MCP server forwards the member's token for writes only.** PGCSpringMCP's reads stay HTTPS
GET on public endpoints without a token, as now. Admin tools call `https://pgcgolf.club/api/admin/...`
with the caller's validated access token from the security context. *My call:* a separate
client for writes (POST/PUT, token-forwarding) so the read client's "GET only" guarantee stays
true and visible.

**D8. Admin conversations need more tool calls.** Today a question may make at most 5 tool calls
(`pgc.chat.max-tool-calls`) within a 35 s deadline. Setting up a tournament from one message
takes more (lookups, create tournament, two events, roster, grouping, tee times). *My call:* for
`profile=admin` members the limit is 15 and the deadline 90 s, both properties; members keep 5 and
35 s. The answer still reports what was done when the limit stops it part way, because each tool
result says what it created.

**D9. Prompt.** `ChatPrompt` restricts the bot to club questions. For admins it gains a short
section: it may perform setup tasks with the admin tools, must resolve names with read tools
rather than guessing ids, and must state exactly what it created or changed (names, dates, ids)
in its answer. Non-admins' prompt is unchanged.

## Interfaces (to be finalised in the plan)

- `/api/admin` endpoints mirror the website's request bodies where those are simple; *my call* is
  small explicit request records rather than whole JPA entities (e.g. `{name, startDate, endDate,
  seasonId, description}`), with validation errors as 400 with a readable message the tool can
  pass to the model. Not-found ids → 404 with a message. Responses return the created/changed
  object's id and key fields so the bot can report them.
- `admin_assign_player`: *my call* — also adds the player to the event's roster if missing
  (the website endpoint does not; without it the player would be grouped but not on the roster).
- Tee time stays a free string as in the website (e.g. "7:30 AM"); the tool description tells the
  model to use the format the club's existing tee times use (`get_event_groups` shows them).
- Error shape from the MCP tools follows the existing read tools' style (`ToolSupport`): a readable
  sentence, never a stack trace or the token.

## Security constraints carried over

- MCP server stays bound to loopback behind nginx; tokens never logged; PII stripping on read
  tools unchanged (write tool results return only what the admin entered plus ids).
- `/api/**` refuses requests with no token, an expired or ID token, a token from another issuer,
  or one without `tools.write`.
- The rule "no `/webapi/admin/**` in the MCP projects" is lifted for this feature by the owner in
  the sense that the MCP server now calls admin functions — through `/api/admin/**`, not
  `/webapi/admin/**`.

## Testing (expectations, details in the plan)

- PGCServer: `/api/admin` endpoints with real JWT validation against a test key (no token → 401,
  ID token → 401, missing scope → 403, valid → works); D5 refusal; D6 website endpoints unchanged
  (before/after tests); D4 filtering in both bots; D8 limits by role.
- PGCSpringMCP: `AccessPolicy` scope rule; each admin tool against a fake `/api/admin`, including
  token forwarding and error messages; read client still GET-only.
- Manual: owner tries a full setup on production with a test tournament, then removes it on the
  website.

## Deployment

Order matters: owner adds the Cognito scope → PGCServer (with DB backup first) → PGCSpringMCP.
Admins sign out and in once so their token carries the new scope. Rollback: previous WAR and the
`.prev` jar, as usual.

## Left to the builder

Class and package names; request record shapes; how the services are split; exact tool
descriptions; test fixtures.

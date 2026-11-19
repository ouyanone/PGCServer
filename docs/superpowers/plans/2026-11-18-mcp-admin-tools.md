# MCP Admin Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Club admins set up tournaments by talking to either club bot; the bots call nine new
`admin_*` MCP tools, which call a new token-only `/api/admin/**` on PGCServer.

**Architecture:** PGCServer gets a second, stateless Spring Security chain for `/api/**`
(Cognito JWT, `token_use=access`, scope `tools.write`) and an `AdminApiController` that calls
services extracted from the website's admin controllers. PGCSpringMCP gets a scope-checking
`AccessPolicy`, a token-forwarding write client and an `AdminTools` class. Both bots filter
`admin_*` tools by the member's `profile` and give admins a larger tool-call budget.

**Tech Stack:** PGCServer: Spring Boot 3.5.16, Java 21, Spring Security 6.5 resource server,
JPA/MySQL, JUnit 5, MockMvc. PGCSpringMCP: Spring Boot 4.1.2, Spring AI 2.0.2, JUnit 5.

**Spec:** `PGCServer/docs/superpowers/specs/2026-11-18-mcp-admin-tools-design.md` (approved
2026-11-18).

## Global Constraints

- Branches: PGCServer `feature-mcp-admin-tools` from `feature-boot-3.5`; PGCSpringMCP
  `feature-admin-tools` from `feature-phase2-oauth`. Uncommitted files already in the trees
  (PROGRESS.md STEP65, static bundle, `.classpath`, the spec) are not part of any task commit
  except where a task says so.
- Tests: JDK 21 (`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home`), system
  `mvn`; PGCServer with `-Dspring.profiles.active=local` (local MySQL, tests roll back).
- Scope names: `https://pgcgolf.club/mcp/tools.read` (existing), `https://pgcgolf.club/mcp/tools.write` (new).
- Admin marker: OIDC `profile` claim equal to `admin` (case-insensitive, as `BaseController`).
- Admin tool names start with `admin_`; that prefix is the only marker the bots rely on.
- The website's `/webapi/**` responses and behaviour do not change.
- Never log tokens, questions or answers. MCP server stays on 127.0.0.1:8900.
- Errors returned to the model are readable sentences, never stack traces or token material.
- Commit trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Commit locally only;
  push/deploy only when the owner asks.

## Rulings made while planning (deviations from the spec)

- **Admin deadline 55 s, not 90 s (spec D8).** nginx's `location /` has the default 60 s
  `proxy_read_timeout` (only `/mcp` has 1 h); 90 s would make nginx answer 504 while the backend
  keeps writing. 55 s stays under it. Cost if wrong: long setups stop earlier and the admin
  continues in a second message.
- **No MCP `readOnlyHint` annotations (spec D4 "where Spring AI supports it").** Spring AI 2.0.2's
  `@Tool` has no annotation attributes; adding them would mean moving off `@Tool`. The `admin_`
  prefix carries the meaning. Cost if wrong: third-party MCP clients don't see the hint.
- **`admin_assign_player` adds to the roster only on the `/api` path**; the website's assign
  endpoint keeps its behaviour (spec non-goal), via a service flag.
- **Admin limit message.** When an admin's question hits the tool-call limit after writes, the
  reply says work may be partly done (Review Focus 1) instead of "couldn't finish looking that up".

## Review Focus

1. Admin hits the tool-call limit or deadline after some writes → the reply must say the setup may
   be partly done and to check the admin screens; never the members' "couldn't finish looking that
   up". (Task 5 test `adminLimitAfterWrites_saysPartlyDone`.)
2. The same create repeated (admin resends after a timeout) → a second tournament with the same
   name and start date is refused with 409 and a message naming the existing id. (Task 3 test
   `createTournament_duplicateNameAndStart_409`.)
3. Event date outside its tournament's dates, or end before start → 400 with a readable message,
   nothing saved. (Task 3 tests `createTournament_endBeforeStart_400`,
   `createEvent_dateOutsideTournament_400`.)
4. Member signed in before the deploy (token lacks `tools.write`) → the admin tool answers "sign
   out and sign in again", not a generic failure. (Task 7 test `adminTool_withoutWriteScope_denied`.)
5. Two players match a name → the bot must not pick one; prompt says to ask. Tools take ids only.
   (Task 5 test `adminPrompt_saysAskWhenNameIsAmbiguous`.)

---

## PGCServer

### Task 1: Token-only security chain for `/api/**`

**Files:**
- Modify: `pom.xml` (add `spring-boot-starter-oauth2-resource-server`, version from the Boot parent)
- Create: `src/main/java/com/shiyuan/config/ApiSecurityConfiguration.java`
- Modify: `src/main/resources/application.properties`, `src/main/resources/application-local.properties`
  (add `pgc.api.write-scope=https://pgcgolf.club/mcp/tools.write`; append `,https://pgcgolf.club/mcp/tools.write`
  to `spring.security.oauth2.client.registration.cognito.scope` in both — both use pool
  `us-east-1_XDeOxg5Ea`, verified to accept the scope on 2026-11-18)
- Test: `src/test/java/com/shiyuan/config/ApiSecurityConfigurationTest.java`

**Interfaces:**
- Produces: `/api/**` is authenticated by bearer JWT only; `/api/admin/**` requires authority
  `SCOPE_https://pgcgolf.club/mcp/tools.write`. `ApiSecurityConfiguration.ACCESS_TOKEN_ONLY`
  (`OAuth2TokenValidator<Jwt>`). Bean `JwtDecoder apiJwtDecoder` (issuer = the existing
  `spring.security.oauth2.client.provider.cognito.issuerUri`, JWKS = issuer + `/.well-known/jwks.json`,
  fetched lazily).

- [ ] **Step 1: Write the failing tests** (`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local")`, `jwt()` post-processor from spring-security-test)

```java
@Test void apiAdmin_noToken_401()              // GET /api/admin/anything → 401, WWW-Authenticate: Bearer
@Test void apiAdmin_readScopeOnly_403()        // jwt().authorities(SCOPE_…/tools.read) → 403
@Test void apiAdmin_writeScope_passesSecurity() // jwt().authorities(SCOPE_…/tools.write) → 404 (unmapped path, not 401/403)
@Test void apiAdmin_sessionLogin_isNotEnough() // oidcLogin() (website session) without bearer → 401
@Test void webapiAdmin_bearerToken_notAuthenticated() // /webapi/admin/tournaments POST with jwt() → not 2xx
@Test void accessTokenOnly_rejectsIdToken()    // ACCESS_TOKEN_ONLY.validate(jwt with token_use=id).hasErrors()
@Test void accessTokenOnly_rejectsMissingTokenUse()
@Test void accessTokenOnly_acceptsAccessToken()
@Test void apiChain_createsNoSession()         // response to /api/admin/x with write jwt has no Set-Cookie
```

- [ ] **Step 2: Run** `mvn -q test -Dspring.profiles.active=local -Dtest=ApiSecurityConfigurationTest` — Expected: FAIL (class missing / 404 where 401 expected).
- [ ] **Step 3: Implement** `ApiSecurityConfiguration`: `@Order(1)` chain with `securityMatcher("/api/**")`, `STATELESS`, CSRF off, `/api/admin/**` `hasAuthority("SCOPE_"+writeScope)`, `anyRequest().authenticated()`, `oauth2ResourceServer(jwt -> jwt.decoder(apiJwtDecoder))`; decoder = `NimbusJwtDecoder.withJwkSetUri(...)` with validators `JwtValidators.createDefaultWithIssuer(issuer)` + `ACCESS_TOKEN_ONLY`. The existing `SecurityConfiguration` chain is untouched (it must not gain a resource server).
- [ ] **Step 4: Run** the test class, then the whole suite. Expected: PASS; suite 179 + new, all green.
- [ ] **Step 5: Commit** `feat(api): token-only security chain for /api/**`.

### Task 2: Extract admin logic into services

**Files:**
- Create: `src/main/java/com/shiyuan/service/GamePlanService.java`, `src/main/java/com/shiyuan/service/TournamentAdminService.java`, `src/main/java/com/shiyuan/service/AdminRequestException.java`
- Modify: `controller/GamePlanController.java` (addPlayers, generateGroups, assignPlayer, updateTee delegate), `controller/TournamentController.java` (create, createEvent delegate)
- Test: `src/test/java/com/shiyuan/service/GamePlanServiceTest.java`, `src/test/java/com/shiyuan/service/TournamentAdminServiceTest.java`, `src/test/java/com/shiyuan/controller/GamePlanControllerTest.java`

**Interfaces:**
- Produces:
  - `AdminRequestException extends RuntimeException` with `int status()`; factories `badRequest(String)`, `notFound(String)`, `conflict(String)`.
  - `GamePlanService`:
    - `AddPlayersResult addPlayers(long eventId, List<Long> playerIds)` → `record AddPlayersResult(List<Long> added, List<Long> alreadyOnRoster)`
    - `List<Map<String,Object>> generateGroups(long eventId, GroupingService.Strategy strategy, boolean refuseIfScored)` (same map shape the website returns today)
    - `TeeRef assignPlayer(long eventId, long playerId, boolean ensureOnRoster)` → `record TeeRef(long teeId, String teeName, String teeTime)`
    - `TeeRef updateTee(long teeId, String teeTime, String teeName)` (null = leave unchanged)
  - `TournamentAdminService`:
    - `Tournament createTournament(Tournament t)` (website path, unchanged semantics)
    - `Event createEvent(long tournamentId, Event e)` (website path, unchanged semantics)
  - `GroupingService.Strategy` becomes `public` if it is not.
- Not-found ids throw `AdminRequestException.notFound("Event 123 does not exist.")`; the website controllers keep today's outward behaviour (they previously threw `RuntimeException`; `AdminRequestException` is one, so status stays what it was).

- [ ] **Step 1: Write characterization tests of the website endpoints first** (`GamePlanControllerTest`, MockMvc with `oidcLogin()` + `profile=admin`, local DB, `@Transactional`): `generate_replacesTeesAndReturnsGroups`, `addPlayers_skipsExisting_201`, `assignPlayer_fillsOpenTee_thenCreatesNewTee`, `updateTee_setsTime`. Run them against the **unchanged** code — Expected: PASS (they pin today's behaviour).
- [ ] **Step 2: Write the failing service tests**: `generateGroups_refuseIfScored_conflictAndNothingDeleted` (an event with a player score `score=82` → `AdminRequestException` status 409, tee and score rows still present), `generateGroups_refuseIfScored_allowsZeroScores`, `generateGroups_emptyRoster_badRequest`, `assignPlayer_ensureOnRoster_addsEventPlayer`, `assignPlayer_withoutFlag_leavesRosterAlone`, `addPlayers_unknownPlayer_notFound`, `updateTee_unknownTee_notFound`, `createEvent_unknownTournament_notFound`. Run — Expected: FAIL (services missing).
- [ ] **Step 3: Implement** the services by moving the controller bodies; controllers call them with `refuseIfScored=false`, `ensureOnRoster=false`. "Scored" = any `PlayerScore` of the event's tees with `score > 0`.
- [ ] **Step 4: Run** service tests, characterization tests, whole suite. Expected: all PASS.
- [ ] **Step 5: Commit** `refactor: admin game-plan and tournament logic into services`.

### Task 3: `/api/admin` — tournaments, events, rewards

**Files:**
- Create: `src/main/java/com/shiyuan/controller/api/AdminApiController.java`, `src/main/java/com/shiyuan/controller/api/AdminApiDtos.java` (request/response records)
- Modify: `TournamentAdminService` (add the `/api` operations below)
- Test: `src/test/java/com/shiyuan/controller/api/AdminApiTournamentTest.java`

**Interfaces:**
- Consumes: Task 1 security; Task 2 services and `AdminRequestException`.
- Produces (all under `jwt()` with write scope; JSON):
  - `POST /api/admin/tournaments` body `CreateTournament(String name, LocalDate startDate, LocalDate endDate, Long seasonId, String description)` → 201 `TournamentView(long id, String name, LocalDate startDate, LocalDate endDate, long seasonId, String seasonName)`
  - `POST /api/admin/tournaments/{id}/events` body `CreateEvent(String name, LocalDate date, Long courseId, String description)` → 201 `EventView(long id, String name, LocalDate date, long courseId, String courseName, long tournamentId)`; the event's season is the tournament's.
  - `POST /api/admin/rewards` body `CreateReward(Long eventId, Long playerId, String rewardName, String rewardDesc, String rewardGroup, Integer displayOrder)` → 201 `RewardView(long id, long eventId, long playerId, String playerName, String rewardName)`; saved through `RewardService.createReward`.
  - Errors: `@ExceptionHandler` local to `AdminApiController`: `AdminRequestException` → its status with `{"error": message}`; bean-validation / missing fields → 400 `{"error": "…"}`.
  - `TournamentAdminService.createTournamentChecked(CreateTournament)`, `createEventChecked(long, CreateEvent)` hold the validation.

- [ ] **Step 1: Write the failing tests**: `createTournament_201_returnsIdAndSeason`, `createTournament_blankName_400`, `createTournament_endBeforeStart_400`, `createTournament_unknownSeason_404`, `createTournament_duplicateNameAndStart_409` (message contains the existing id), `createEvent_201_inheritsSeason`, `createEvent_dateOutsideTournament_400`, `createEvent_unknownCourse_404`, `createReward_201`, `createReward_unknownPlayer_404`, `createReward_blankName_400`, `noToken_401`.
- [ ] **Step 2: Run** `-Dtest=AdminApiTournamentTest` — Expected: FAIL (404 for unmapped paths).
- [ ] **Step 3: Implement** controller, records and checked service methods.
- [ ] **Step 4: Run** the class and the whole suite — Expected: PASS.
- [ ] **Step 5: Commit** `feat(api): /api/admin tournaments, events and rewards`.

### Task 4: `/api/admin` — roster, grouping, late player, tee time

**Files:**
- Modify: `AdminApiController.java`, `AdminApiDtos.java`
- Test: `src/test/java/com/shiyuan/controller/api/AdminApiGamePlanTest.java`

**Interfaces:**
- Consumes: `GamePlanService` (Task 2).
- Produces:
  - `POST /api/admin/events/{eventId}/players` body `AddPlayers(List<Long> playerIds)` → 200 `AddPlayersResult`
  - `POST /api/admin/events/{eventId}/groups` body `GenerateGroups(String strategy)` → 200 groups list; calls `generateGroups(id, strategy, true)`. Unknown strategy → 400 whose message lists the six names.
  - `POST /api/admin/events/{eventId}/players/{playerId}/assign` → 200 `TeeRef`; `ensureOnRoster=true`.
  - `PUT /api/admin/tees/{teeId}` body `SetTee(String teeTime, String teeName)` → 200 `TeeRef`; both blank → 400.

- [ ] **Step 1: Write the failing tests**: `addPlayers_200_reportsAddedAndExisting`, `addPlayers_emptyList_400`, `generateGroups_200`, `generateGroups_scoredEvent_409_nothingDeleted`, `generateGroups_unknownStrategy_400_listsStrategies`, `assign_200_addsToRoster`, `setTee_200`, `setTee_nothingToChange_400`, `setTee_unknownTee_404`.
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** class + whole suite — Expected: PASS.
- [ ] **Step 5: Commit** `feat(api): /api/admin roster, grouping, late player and tee time`.

### Task 4B: `/api/admin` — players

**Files:**
- Modify: `AdminApiController.java`, `AdminApiDtos.java`, `service/PlayerService.java` + `service/impl/PlayerServiceImpl.java` (add the two methods below; `savePlayer` and the website endpoint unchanged)
- Test: `src/test/java/com/shiyuan/controller/api/AdminApiPlayerTest.java`

**Interfaces:**
- Produces:
  - `PlayerFields(String firstName, String lastName, String nickName, String chineseNickName, String ghinNumber, String gender, Double handicap, Boolean active)` — the only writable fields; no phone, email, PGC handicap, points or averages.
  - `PlayerView(long id, String firstName, String lastName, String nickName, String chineseNickName, String ghinNumber, String gender, double handicap, boolean active)` — never phone or email.
  - `POST /api/admin/players` body `PlayerFields` → 201 `PlayerView`; first and last name required (400); gender `M`/`F` or absent (400 otherwise); `active` defaults to true; a player with the same first and last name (case-insensitive) or the same non-blank GHIN number → 409 naming the existing id.
  - `PATCH /api/admin/players/{id}` body `PlayerFields` → 200 `PlayerView`; only non-null fields change; all null → 400; unknown id → 404; a GHIN number already used by another player → 409.
  - `PlayerService.addPlayer(PlayerFields)`, `PlayerService.updatePlayer(long id, PlayerFields)` throw `AdminRequestException`.

- [ ] **Step 1: Write the failing tests**: `addPlayer_201_noContactFieldsInResponse` (response JSON has no `phone`/`email` keys), `addPlayer_missingLastName_400`, `addPlayer_badGender_400`, `addPlayer_duplicateName_409`, `addPlayer_duplicateGhin_409`, `updatePlayer_changesOnlyGivenFields` (existing phone, email, pgcHandicap, points untouched), `updatePlayer_nothingToChange_400`, `updatePlayer_unknown_404`, `updatePlayer_ghinTakenByOther_409`, `updatePlayer_bodyWithEmail_ignored` (unknown JSON keys like `email` do not change the record).
- [ ] **Step 2: Run** `-Dtest=AdminApiPlayerTest` — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** class + whole suite — Expected: PASS.
- [ ] **Step 5: Commit** `feat(api): /api/admin add and update player`.

### Task 5: J bot — admin role, tool filter, limits, prompt

**Files:**
- Create: `src/main/java/com/shiyuan/chat/ChatRole.java`, `src/main/java/com/shiyuan/chat/QuestionLimits.java`
- Modify: `chat/ChatAssistant.java`, `chat/ChatPrompt.java`, `chat/ChatProperties.java`, `chat/ChatConfig.java`, `controller/ChatController.java`
- Test: `ChatRoleTest`, `ChatAssistantTest`, `ChatPromptTest`, `ChatControllerTest` (existing classes gain tests)

**Interfaces:**
- Produces:
  - `enum ChatRole { MEMBER, ADMIN; static ChatRole of(OidcUser member); boolean mayUse(String toolName); }` — ADMIN iff `"admin".equalsIgnoreCase(member.getProfile())`; MEMBER may use any tool not starting with `admin_`.
  - `record QuestionLimits(int maxToolCalls, Duration deadline)`.
  - `ChatProperties`: `adminMaxToolCalls` (default 15), `adminDeadlineSeconds` (default 55); members keep `maxToolCalls` 5 and 35 s.
  - `ChatAssistant(ModelGateway, ChatPricing, String modelId, QuestionLimits member, QuestionLimits admin, Clock)`; existing constructors stay and use the same limits for both roles.
  - `ChatAssistant.answer(String question, Optional<PlayerRef> me, List<ChatExchangeDto> history, McpSession mcp, ChatUsage usage, LocalDate today, ChatRole role)`; the existing 6-argument `answer` delegates with `ChatRole.MEMBER`.
  - `ChatAssistant.ADMIN_STOPPED = "I stopped before finishing, so the setup may be partly done. Check the admin pages, then tell me what's left."`
  - `ChatPrompt.system(Optional<PlayerRef> me, LocalDate today, ChatRole role)`; the 2-argument form = MEMBER, text unchanged.
- Admin prompt section (exact copy, appended for ADMIN only):

```
- You are talking to a club admin. You may set up tournaments, events, rosters, groups, tee
  times and rewards, and add or update players, with the admin_ tools. Phone numbers and
  emails cannot be set here; say so if asked.
- Turn names into ids with the read tools (find_player, list_courses, list_seasons,
  list_tournaments, list_events, get_event_groups). Never guess an id. If a name matches more
  than one player, or none, ask which one before changing anything.
- After changing anything, say exactly what you created or changed: names, dates and ids.
```

- [ ] **Step 1: Write the failing tests**:
  - `ChatRoleTest`: `adminProfile_isAdmin`, `adminProfileAnyCase_isAdmin`, `noProfile_isMember`, `member_mayNotUseAdminTools`, `admin_mayUseAll`.
  - `ChatAssistantTest`: `member_adminToolsNotSentToModel` (fake MCP lists `get_standings` + `admin_create_tournament`; the model request's tools contain only `get_standings`), `admin_adminToolsSentToModel`, `member_modelCallsAdminTool_refusedNotForwarded` (model asks for `admin_create_tournament` anyway → MCP gets no `tools/call`, tool result is an error text), `admin_getsAdminLimits` (6 tool calls allowed for admin with limits 15; member stops at 5), `adminLimitAfterWrites_saysPartlyDone` (admin over the limit → `ADMIN_STOPPED`).
  - `ChatPromptTest`: `memberPrompt_unchanged` (equals today's text), `adminPrompt_mentionsAdminTools`, `adminPrompt_saysAskWhenNameIsAmbiguous`.
  - `ChatControllerTest`: `adminProfile_passesAdminRole` (oidcLogin with claim `profile=admin` → `answer(..., ChatRole.ADMIN)`), `noProfile_passesMemberRole`.
- [ ] **Step 2: Run** those classes — Expected: FAIL (types missing).
- [ ] **Step 3: Implement.** Filter `mcp.tools()` by `role.mayUse` before `model.start`; refuse a disallowed tool call without calling MCP; choose limits by role; on hitting the limit return `ADMIN_STOPPED` for ADMIN, `TOO_MANY_LOOKUPS` for MEMBER.
- [ ] **Step 4: Run** the classes and the whole suite — Expected: PASS.
- [ ] **Step 5: Commit** `feat(chat): admin role, admin tool filter and limits in the SDK bot`.

### Task 6: S bot — the same rules

**Files:**
- Modify: `chat/springai/SpringAiChatAssistant.java`, `chat/springai/SpringAiChatConfig.java`, `controller/SpringAiChatController.java`
- Test: `SpringAiChatAssistantTest`, `SpringAiChatControllerTest`

**Interfaces:**
- Consumes: `ChatRole`, `QuestionLimits`, `ChatPrompt.system(me, today, role)`, `ChatAssistant.ADMIN_STOPPED` (Task 5).
- Produces: `SpringAiChatAssistant.answer(String, Optional<PlayerRef>, List<ChatExchangeDto>, String accessToken, ChatUsage, LocalDate, ChatRole)`; the 6-argument form delegates with MEMBER. Config builds member limits (`maxToolCalls`, 35 s) and admin limits (`adminMaxToolCalls`, `adminDeadlineSeconds`).

- [ ] **Step 1: Write the failing tests**: `member_adminToolsNotSentToClaude` (FakeAnthropicServer request body has no `admin_` tool), `admin_adminToolsSentToClaude`, `member_claudeCallsAdminTool_notForwardedToMcp`, `admin_getsAdminLimits`, `adminLimitAfterWrites_saysPartlyDone`, `adminPrompt_used_forAdmin`; controller: `adminProfile_passesAdminRole`, `noProfile_passesMemberRole`.
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement.** Filter `session.toolCallbacks(...)` by `role.mayUse(cb.getToolDefinition().name())`; a tool call naming a filtered tool is answered with an error result, not executed.
- [ ] **Step 4: Run** classes + whole suite — Expected: PASS.
- [ ] **Step 5: Commit** `feat(chat): admin role, admin tool filter and limits in the Spring AI bot`.

---

## PGCSpringMCP

### Task 7: Write-scope access policy

**Files:**
- Create: `src/main/java/club/pgcgolf/springmcp/policy/WriteScopePolicy.java`
- Modify: `src/main/resources/application.properties` (add `pgc.mcp.write-scope=${pgc.mcp.resource-id}/tools.write`)
- Modify: `src/test/java/club/pgcgolf/springmcp/security/TestTokens.java` (add `public String accessTokenWithScopes(String... scopes)`)
- Test: `src/test/java/club/pgcgolf/springmcp/policy/WriteScopePolicyTest.java`, `AuthenticatedToolCallTest` (one new case)

**Interfaces:**
- Produces: `@Component WriteScopePolicy implements AccessPolicy` — tools starting `admin_` need the write scope in the token's space-separated `scope` claim; others allowed. Deny reason (exact): `"This tool changes club data and needs the tools.write permission. Sign out of pgcgolf.club and sign in again, then retry."`

- [ ] **Step 1: Write the failing tests**: `readTool_allowedWithoutWriteScope`, `adminTool_withWriteScope_allowed`, `adminTool_withoutWriteScope_denied` (reason equals the exact text), `adminTool_noScopeClaim_denied`, `scopeMatchIsExact_notSubstring` (`…/tools.writer` denied); `AuthenticatedToolCallTest.adminTool_readOnlyToken_returnsDenialText` once Task 9's tools exist (write it now against a test-only `admin_` tool if needed, else add in Task 9).
- [ ] **Step 2: Run** `mvn -q test -Dtest=WriteScopePolicyTest` — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** class + whole suite — Expected: PASS (61 existing + new).
- [ ] **Step 5: Commit** `feat(policy): admin_ tools need the tools.write scope`.

### Task 8: Token-forwarding write client

**Files:**
- Create: `src/main/java/club/pgcgolf/springmcp/PgcAdminApi.java`
- Modify: `PgcSpringMcpApplication.java` (bean `PgcAdminApi`, reusing `pgcApiRestClient`'s base URL, timeouts and no-redirect settings through a second `RestClient` bean `pgcAdminRestClient`)
- Test: `src/test/java/club/pgcgolf/springmcp/PgcAdminApiTest.java` (uses `client/StubServer`)

**Interfaces:**
- Produces: `PgcAdminApi`:
  - `Map<String,Object> post(String path, Object body)`, `Map<String,Object> put(String path, Object body)`, `List<Map<String,Object>> postForList(String path, Object body)`.
  - Path must start with `/api/admin/` or `IllegalArgumentException`.
  - Bearer token = the `Jwt` in `SecurityContextHolder`; none → `PgcApiException("no caller token", 401)` without sending.
  - Non-2xx → `PgcApiException(serverErrorText, status)` where `serverErrorText` is the response's `error` field when present.
  - `PgcApi` (reads) is unchanged and still GET-only.

- [ ] **Step 1: Write the failing tests**: `post_forwardsCallerBearerToken` (stub records `Authorization: Bearer <token>`), `post_noCaller_doesNotSend`, `path_outsideApiAdmin_rejected`, `status409_errorTextSurfaced`, `redirect_isFailure`, `readClient_stillGetOnly` (existing PgcApi has no non-GET method — reflection check on public methods).
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** class + whole suite — Expected: PASS.
- [ ] **Step 5: Commit** `feat: token-forwarding client for /api/admin`.

### Task 9: Admin tools and two read tools

**Files:**
- Create: `src/main/java/club/pgcgolf/springmcp/tools/AdminTools.java`
- Modify: `tools/CompetitionTools.java` (add `list_seasons` → `GET /webapi/seasons`; `get_event_groups(eventId)` → `GET /webapi/gameplan/{eventId}/groups`, returning tee id, name, time and player names/ids only), `PgcSpringMcpApplication.java` (register `AdminTools` in `pgcToolCallbacks`), `test/.../ToolDiscoveryTest.java` (12 → 23)
- Test: `src/test/java/club/pgcgolf/springmcp/tools/AdminToolsTest.java`, `CompetitionToolsTest` (new cases)

**Interfaces:**
- Consumes: `PgcAdminApi` (Task 8; add `Map<String,Object> patch(String path, Object body)`), `WriteScopePolicy` (Task 7), PGCServer `/api/admin` contract (Tasks 3, 4, 4B).
- Produces nine `@Tool`s (names exact): `admin_create_tournament(name, startDate, endDate, seasonId, description?)`, `admin_create_event(tournamentId, name, date, courseId, description?)`, `admin_add_players_to_event(eventId, playerIds)`, `admin_generate_groups(eventId, strategy)`, `admin_assign_player(eventId, playerId)`, `admin_set_tee_time(teeId, teeTime, teeName?)`, `admin_create_reward(eventId, playerId, rewardName, rewardDesc?, rewardGroup?, displayOrder?)`, `admin_add_player(firstName, lastName, nickName?, chineseNickName?, ghinNumber?, gender?, handicap?, active?)`, `admin_update_player(playerId, the same fields all optional)`; the player tools' descriptions say phone and email cannot be set here. Dates as `yyyy-MM-dd`. Each returns a one-paragraph summary plus the created/changed ids; on `PgcApiException` returns the server's message (409/400/404) or "the club server refused the change (HTTP n)". `admin_generate_groups`' description names the six strategies and says it refuses once scores exist; `admin_set_tee_time`'s says to copy the format of existing tee times from `get_event_groups`.

- [ ] **Step 1: Write the failing tests** (stub `PgcAdminApi`): one success test per tool asserting path, body and summary; `generateGroups_conflict_returnsServerMessage`; `createTournament_badDate_explainsFormat` (`"May 3"` → readable error, nothing sent); `listSeasons_returnsIdsAndNames`; `getEventGroups_noPii`; `ToolDiscoveryTest` count 23 and names include all nine `admin_*`; `updatePlayer_sendsOnlyGivenFields` (PATCH body has no null keys); `AuthenticatedToolCallTest.adminTool_readOnlyToken_returnsDenialText`.
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** whole suite — Expected: PASS.
- [ ] **Step 5: Commit** `feat(tools): nine admin tools, list_seasons and get_event_groups`.

### Task 10: Docs and progress

**Files:**
- Modify: `PGCSpringMCP/docs/cognito-setup.md` (new section "Adding tools.write": the console steps the owner did on 2026-11-18, and the authorize probe that verifies them)
- Modify: `PGCServer/PROGRESS.md` (STEP66: what was built, test counts, rulings)

- [ ] **Step 1:** Write both. **Step 2:** Run both full suites one last time; record counts in STEP66. **Step 3: Commit** each repo `docs: admin tools`.

## Deployment (after final review; owner's go-ahead required)

Back up the DB → deploy PGCServer WAR (UI unchanged unless the UI branch is included) → deploy
PGCSpringMCP jar (keep `.prev`) → verify: `/api/admin/tournaments` without token → 401; `/mcp`
`tools/list` with a fresh owner token shows 23 tools → owner signs out/in and runs the spec's
TEST Cup walkthrough in both bots → owner cleans up on the website.

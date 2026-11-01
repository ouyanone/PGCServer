# PGCSpringMCP Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve three read-only PGC tools over stateless Streamable HTTP behind a validated Cognito access token, with every step of the token's life observable.

**Architecture:** A standalone Spring Boot jar on `127.0.0.1:8900`, proxied by the existing nginx at `https://pgcgolf.club/mcp`. Spring AI's MCP server starter in `STATELESS` protocol mode exposes `@Tool`-annotated methods; Spring Security validates the JWT as a resource server; an `AccessPolicy` seam carries the owner's own authorization logic. A client in the same project drives the whole OAuth discovery chain and prints it.

**Tech Stack:** Java 21, Spring Boot 4.1.2, `spring-ai-starter-mcp-server-webmvc` 2.0.2, Spring Security resource server, Spring `RestClient`, JUnit 5, Nimbus JOSE (test-only JWT signing).

**Spec:** [docs/superpowers/specs/2026-10-31-pgc-spring-mcp-phase2-design.md](../specs/2026-10-31-pgc-spring-mcp-phase2-design.md)

## Global Constraints

- Java 21. EC2 runs Corretto 21.0.4; do not use a later language level.
- `spring-ai-starter-mcp-server-webmvc` version `2.0.2` (2.1.0-RC1 exists; do not use it). It pulls Spring Boot `4.1.2`.
- Stateless mode: `spring.ai.mcp.server.protocol=STATELESS`. The enum values are `SSE`, `STREAMABLE`, `STATELESS`; the default is `STREAMABLE`.
- Endpoint property is `spring.ai.mcp.server.streamable-http.mcp-endpoint`, default `/mcp`. (The Spring AI docs table names `spring.ai.mcp.server.stateless.mcp-endpoint`; that key does not exist in 2.0.2's generated configuration metadata. Verified 2026-11-01.)
- Bind `server.address=127.0.0.1`, `server.port=8900`. Never bind `0.0.0.0`.
- `phone` and `email` are stripped from every tool result, unconditionally and not configurably.
- HTTP GET only, to paths under `/webapi/`. No path containing `/admin/` may appear anywhere in this project.
- **Fail closed:** if a token has no `aud`, or `aud` does not name this resource, the request is rejected. Never fall back to `client_id`.
- No secrets in git: no client secret, no captured token, no `application-local.properties` with credentials.
- `PGCMcp`, `PGCServer` and `PGCUI` source are not modified by this plan.
- Tool annotation is `org.springframework.ai.tool.annotation.Tool`; registration is `org.springframework.ai.tool.method.MethodToolCallbackProvider` returning a `org.springframework.ai.tool.ToolCallbackProvider` bean.

## Review Focus

- **A token minted for a different audience** (e.g. the live site's app client) presented to `/mcp` — must be rejected with 401, not accepted because the issuer matched. Pinned in Task 5.
- **A malformed `Authorization` header** (`Bearer` with no token, a non-Bearer scheme, a token that is not three dot-separated segments) — must yield 401 with the `WWW-Authenticate` challenge, never a 500 or a stack trace. Pinned in Task 6.
- **The upstream club API unreachable, slow, or returning 500** — must become `isError` text in the tool result within a bounded time, never a hung request or a propagated exception. Mapped to an exception in Task 3; turned into text, and proved not to escape, in Task 4.
- **A player row that actually has `phone`/`email` populated** — production holds 2 emails and 1 phone across 109 players, so a row exists that would leak if the strip is keyed off the wrong field name. Pinned in Task 4.
- **An `AccessPolicy` implementation that throws** — the owner writes this code later; a bug in it must deny and say so, not 500 or leak a stack trace. Pinned in Task 7.

---

### Task 1: The `aud` gate

The design stops here if Cognito will not bind an audience. No server code until this passes. **Tasks 2–4 do not depend on this task** and may proceed while waiting on the owner.

**Files:**
- Create: `docs/cognito-setup.md`

- [ ] **Step 1: Write the runbook** in `docs/cognito-setup.md` for the owner: create a new throwaway user pool in `us-east-1`; add a resource server with identifier `https://pgcgolf.club/mcp` and one custom scope `tools.read`; create a **confidential** app client (authorization-code grant, PKCE, allowed callback `http://127.0.0.1:8765/callback`) with that scope enabled; create one test user with a known password and no MFA. Record the pool id, issuer URI, client id, and the domain prefix. State that the client secret goes in the owner's own notes, never in this repo.

- [ ] **Step 2: Obtain one access token by hand** and record its decoded claims (header and payload, signature omitted) in `docs/cognito-setup.md`. The authorize request must carry `resource=https://pgcgolf.club/mcp`.

Expected: the payload contains `"aud": "https://pgcgolf.club/mcp"`, `"token_use": "access"`, `"iss"` matching the new pool, and `"scope"` containing `https://pgcgolf.club/mcp/tools.read`.

- [ ] **Step 3: Decide**

If `aud` is present and correct: record it and continue. If `aud` is absent: **stop**, write what was observed in `docs/cognito-setup.md`, and report to the owner — per the spec's §10 this is their decision between a different authorization server and knowingly accepting `client_id` as a surrogate. Do not proceed to Task 5 on an assumption.

- [ ] **Step 4: Commit**

```bash
git add docs/cognito-setup.md
git commit -m "docs: record the Cognito resource-server setup and the aud gate result"
```

---

### Task 2: Project skeleton and a reachable stateless MCP endpoint

**Files:**
- Create: `pom.xml`, `src/main/java/club/pgcgolf/springmcp/PgcSpringMcpApplication.java`, `src/main/resources/application.properties`
- Test: `src/test/java/club/pgcgolf/springmcp/ApplicationContextTest.java`

**Interfaces:**
- Produces: `club.pgcgolf.springmcp.PgcSpringMcpApplication` — the Spring Boot entry point. Package root for everything else is `club.pgcgolf.springmcp`.

- [ ] **Step 1: Write the failing test** `ApplicationContextTest` with `@SpringBootTest(webEnvironment = RANDOM_PORT)`:

The properties type is `org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties`; the enum is its nested `ServerProtocol`.

```java
@Test
void contextLoads_withStatelessProtocolSelected() {
    assertEquals(McpServerProperties.ServerProtocol.STATELESS, properties.getProtocol());
}

@Test
void mcpEndpoint_isMountedAtSlashMcp() {
    // POST an empty body to /mcp; assert the status is NOT 404
    assertNotEquals(404, status);
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q test -Dtest=ApplicationContextTest`
Expected: FAIL — no `pom.xml` / no application class.

- [ ] **Step 3: Create `pom.xml`** — `groupId` `club.pgcgolf`, `artifactId` `pgc-spring-mcp`, `version` `1.0.0`, packaging `jar`, `<finalName>pgc-spring-mcp</finalName>`, Java release 21, parent `spring-boot-starter-parent:4.1.2`, dependencies: `spring-ai-starter-mcp-server-webmvc:2.0.2`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-test` (test), `nimbus-jose-jwt` (test). `spring-boot-maven-plugin` for the executable jar.

- [ ] **Step 4: Create the application class and `application.properties`** — `server.address=127.0.0.1`, `server.port=8900`, `spring.ai.mcp.server.protocol=STATELESS`, `spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp`, `spring.ai.mcp.server.name=pgc-golf`, `spring.ai.mcp.server.version=1.0.0` (matching the artifact version, so the handshake reports something true). Security is not configured yet, so permit all requests for now with a `SecurityFilterChain` that Task 5 replaces.

- [ ] **Step 5: Run the tests**

Run: `mvn -q test -Dtest=ApplicationContextTest`
Expected: PASS, 2 tests.

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/
git commit -m "feat: stateless MCP server skeleton on 127.0.0.1:8900"
```

---

### Task 3: `PgcApi` — the one way out to the club API

**Files:**
- Create: `src/main/java/club/pgcgolf/springmcp/PgcApi.java`, `src/main/java/club/pgcgolf/springmcp/PgcApiException.java`
- Test: `src/test/java/club/pgcgolf/springmcp/PgcApiTest.java`

**Interfaces:**
- Produces:
  - `PgcApi(RestClient restClient)` — constructor injection.
  - `List<Map<String, Object>> getList(String path)`
  - `Map<String, Object> getObject(String path)`
  - `PgcApiException extends RuntimeException` with `int status()`; status `0` means the request never completed.
  - `static final String ALLOWED_PREFIX = "/webapi/"`

- [ ] **Step 1: Write the failing tests** in `PgcApiTest`, driving a `RestClient` bound to `MockRestServiceServer`:

```java
@Test void getList_returnsTheRowsTheApiSent()
@Test void getList_rejectsAPathOutsideWebapi()          // "/admin/x" -> IllegalArgumentException
@Test void getList_rejectsAPathContainingAdmin()        // "/webapi/admin/players" -> IllegalArgumentException
@Test void getList_mapsA404ToStatus404()                // PgcApiException, status() == 404
@Test void getList_mapsA500ToStatus500()
@Test void getList_mapsAConnectionFailureToStatusZero() // PgcApiException, status() == 0
@Test void getObject_returnsTheSingleObject()
```

Each exception message must name the path.

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -q test -Dtest=PgcApiTest`
Expected: FAIL — `PgcApi` not defined.

- [ ] **Step 3: Implement `PgcApi` and `PgcApiException`.** Reject any path not starting with `ALLOWED_PREFIX` and any path containing `/admin/` before the request is made. GET only.

- [ ] **Step 4: Define the `RestClient` bean** in the application class: base URL from `pgc.api.base-url` (default `https://pgcgolf.club`), 10s connect and 20s read timeouts via `ClientHttpRequestFactorySettings`.

- [ ] **Step 5: Run the tests**

Run: `mvn -q test -Dtest=PgcApiTest`
Expected: PASS, 7 tests.

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: PgcApi, the only way out to the club API"
```

---

### Task 4: The three tools

**Files:**
- Create: `src/main/java/club/pgcgolf/springmcp/tools/PgcTools.java`, `src/main/java/club/pgcgolf/springmcp/tools/Pii.java`
- Modify: the application class, to add the `ToolCallbackProvider` bean
- Test: `src/test/java/club/pgcgolf/springmcp/tools/PgcToolsTest.java`, `src/test/java/club/pgcgolf/springmcp/tools/PiiTest.java`, `src/test/java/club/pgcgolf/springmcp/ToolDiscoveryTest.java`

**Interfaces:**
- Consumes: `PgcApi.getList(String)`, `PgcApi.getObject(String)`, `PgcApiException.status()`
- Produces:
  - `@Service PgcTools(PgcApi api)` with three `@Tool`-annotated methods:
    - `String listCourses()` — tool name `list_courses`
    - `String findPlayer(String name)` — tool name `find_player`
    - `String getPlayerProfile(long playerId)` — tool name `get_player_profile`
  - `Pii.strip(Map<String, Object> row) -> Map<String, Object>` — removes `phone` and `email`, drops null values.
  - Each tool returns a JSON string. On a `PgcApiException` it returns a plain-text explanation naming the condition, never throws.

- [ ] **Step 1: Write the failing tests**

`PiiTest`:
```java
@Test void strip_removesPhoneAndEmail()
@Test void strip_removesThemEvenWhenPopulated()   // a row with real-looking values, both gone
@Test void strip_dropsNullValues()
@Test void strip_keepsEverythingElse()
```

`PgcToolsTest`, against a stubbed `PgcApi`:
```java
@Test void listCourses_returnsEveryCourse()
@Test void listCourses_neverIncludesPhoneOrEmail()
@Test void findPlayer_matchesOnPartOfANameCaseInsensitively()
@Test void findPlayer_saysSoWhenNothingMatches()
@Test void findPlayer_neverIncludesPhoneOrEmail()
@Test void getPlayerProfile_returnsTheProfile()
@Test void getPlayerProfile_explainsAnUnknownId()        // api throws status 404 AND status 500
@Test void everyTool_reportsAnUnreachableApiAsText()     // api throws status 0; assert no exception escapes
```

`ToolDiscoveryTest` with `@SpringBootTest`:
```java
@Test void threeToolsAreRegistered()
@Test void theirNamesAre_listCourses_findPlayer_getPlayerProfile()
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -q test -Dtest='PiiTest,PgcToolsTest,ToolDiscoveryTest'`
Expected: FAIL — `PgcTools` and `Pii` not defined.

- [ ] **Step 3: Implement `Pii` and `PgcTools`.** `list_courses` reads `/webapi/courses` and emits `id`, `courseName`, `par`, `courseRating`, and `slope` mapped from the upstream field `slop`. `find_player` reads `/webapi/players` and matches `fName`, `lName`, `"fName lName"` and `nickName` case-insensitively, emitting `playerId` (from upstream `id`), `fName`, `lName`, `nickName`, `pgcHandicap`. `get_player_profile` reads `/webapi/players/{playerId}`. Every row passes through `Pii.strip`.

- [ ] **Step 4: Register the tools** — add a `ToolCallbackProvider` bean built with `MethodToolCallbackProvider.builder().toolObjects(pgcTools).build()`.

- [ ] **Step 5: Run the tests**

Run: `mvn -q test -Dtest='PiiTest,PgcToolsTest,ToolDiscoveryTest'`
Expected: PASS, 14 tests.

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: list_courses, find_player and get_player_profile"
```

---

### Task 5: Resource server — validate the token, fail closed

**Files:**
- Create: `src/main/java/club/pgcgolf/springmcp/security/SecurityConfig.java`, `src/main/java/club/pgcgolf/springmcp/security/AudienceValidator.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/club/pgcgolf/springmcp/security/AudienceValidatorTest.java`, `src/test/java/club/pgcgolf/springmcp/security/TokenValidationTest.java`, `src/test/java/club/pgcgolf/springmcp/security/TestTokens.java`

**Interfaces:**
- Consumes: the issuer and audience recorded in Task 1.
- Produces:
  - `AudienceValidator(String requiredAudience) implements OAuth2TokenValidator<Jwt>`
  - `SecurityConfig` — a `SecurityFilterChain` requiring an authenticated JWT for `/mcp/**`, and a `JwtDecoder` bean wired with issuer, audience and `token_use` validators. Replaces Task 2's permit-all chain.
  - `TestTokens` (test fixture) — signs JWTs with a generated RSA key and serves the matching JWKS, so no network and no real Cognito.

- [ ] **Step 1: Write the failing tests**

`AudienceValidatorTest`:
```java
@Test void accepts_whenAudContainsTheRequiredResource()
@Test void rejects_whenAudNamesADifferentResource()
@Test void rejects_whenAudIsAbsent()              // the fail-closed case
@Test void rejects_whenAudIsAnEmptyList()
```

`TokenValidationTest`, a `@SpringBootTest` posting to `/mcp` with a `Bearer` token from `TestTokens`:
```java
@Test void accepts_aWellFormedTokenForThisResource()        // not 401
@Test void rejects_aTokenWithNoAudience()                   // 401
@Test void rejects_aTokenForAnotherAudience()               // 401  <- Review Focus
@Test void rejects_aTokenFromAnotherIssuer()                // 401
@Test void rejects_anExpiredToken()                         // 401
@Test void rejects_aTokenSignedByTheWrongKey()              // 401
@Test void rejects_anIdTokenRatherThanAnAccessToken()       // 401, token_use != "access"
@Test void rejects_aRequestWithNoAuthorizationHeader()      // 401
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -q test -Dtest='AudienceValidatorTest,TokenValidationTest'`
Expected: FAIL — `AudienceValidator` not defined; `/mcp` currently permits all.

- [ ] **Step 3: Implement `AudienceValidator` and `SecurityConfig`.** Read `pgc.mcp.resource-id` and `spring.security.oauth2.resourceserver.jwt.issuer-uri` from properties. Compose the audience validator with the default issuer and timestamp validators, plus a `token_use == "access"` check. A JWT with no `aud` claim fails.

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest='AudienceValidatorTest,TokenValidationTest'`
Expected: PASS, 12 tests.

- [ ] **Step 5: Commit**

```bash
git add src/ src/main/resources/application.properties
git commit -m "feat: validate the access token as a resource server, failing closed on a missing aud"
```

---

### Task 6: Discovery — the `401` challenge and the protected-resource metadata

**Files:**
- Create: `src/main/java/club/pgcgolf/springmcp/security/ProtectedResourceMetadataController.java`
- Modify: `src/main/java/club/pgcgolf/springmcp/security/SecurityConfig.java`
- Test: `src/test/java/club/pgcgolf/springmcp/security/DiscoveryTest.java`

**Interfaces:**
- Consumes: `SecurityConfig` from Task 5.
- Produces: `GET /.well-known/oauth-protected-resource/mcp` returning the RFC 9728 document, unauthenticated.

- [ ] **Step 1: Write the failing tests** in `DiscoveryTest`:

```java
@Test void unauthenticatedCallToMcp_returns401()
@Test void thatResponseCarriesAWwwAuthenticateBearerChallenge()
@Test void thatChallengeNamesTheResourceMetadataUrl()   // resource_metadata="https://pgcgolf.club/.well-known/oauth-protected-resource/mcp"
@Test void metadataDocument_isReachableWithoutAToken()
@Test void metadataDocument_namesThisResourceAndItsAuthorizationServer()
@Test void malformedBearerHeader_returns401NotA500()    // "Bearer", "Bearer  ", "Basic abc", "Bearer not.a.jwt"  <- Review Focus
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -q test -Dtest=DiscoveryTest`
Expected: FAIL — no metadata endpoint; challenge lacks `resource_metadata`.

- [ ] **Step 3: Implement the metadata controller** returning `resource` (the value of `pgc.mcp.resource-id`), `authorization_servers` (a one-element list holding the issuer URI), `scopes_supported`, and `bearer_methods_supported: ["header"]`. Permit it unauthenticated in `SecurityConfig`.

- [ ] **Step 4: Add the `resource_metadata` parameter to the challenge** by customising the `BearerTokenAuthenticationEntryPoint`, using `pgc.mcp.public-base-url` (default `https://pgcgolf.club`) so the advertised URL is the one a client can actually reach — not `127.0.0.1:8900`.

- [ ] **Step 5: Run the tests**

Run: `mvn -q test -Dtest=DiscoveryTest`
Expected: PASS, 6 tests.

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: RFC 9728 protected-resource metadata and a discoverable 401 challenge"
```

---

### Task 7: The `AccessPolicy` seam

**Files:**
- Create: `src/main/java/club/pgcgolf/springmcp/policy/TokenPrincipal.java`, `AccessDecision.java`, `AccessPolicy.java`, `ToolAccessAspect.java`
- Modify: `src/main/java/club/pgcgolf/springmcp/tools/PgcTools.java`
- Test: `src/test/java/club/pgcgolf/springmcp/policy/AccessPolicyTest.java`

**Interfaces:**
- Consumes: the validated `Jwt` from Spring Security's `SecurityContext`.
- Produces:
  - `record TokenPrincipal(String login, String subject, Map<String, Object> claims)`
  - `record AccessDecision(boolean allowed, String reason)` with `static allow()` and `static deny(String reason)`
  - `interface AccessPolicy { AccessDecision decide(TokenPrincipal caller, String toolName); AccessPolicy ALLOW_ALL = (c, t) -> AccessDecision.allow(); }`
  - `TokenPrincipal.from(Jwt jwt)` — `login` prefers the claim recorded in Task 1, falling back to `username` then `sub`.

- [ ] **Step 1: Write the failing tests** in `AccessPolicyTest`:

```java
@Test void from_readsTheLoginAndSubjectOutOfTheJwt()
@Test void from_fallsBackToSubWhenNoLoginClaimIsPresent()
@Test void allowAll_allowsEveryTool()
@Test void aDenyBecomesIsErrorTextInTheToolResult()      // not a 401, not an exception
@Test void theDenyReasonReachesTheCaller()
@Test void aPolicyThatThrows_deniesAndDoesNotLeakTheStackTrace()   // <- Review Focus
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -q test -Dtest=AccessPolicyTest`
Expected: FAIL — types not defined.

- [ ] **Step 3: Implement the three types and `ToolAccessAspect`.** The aspect wraps each `@Tool` method, builds a `TokenPrincipal` from the current `SecurityContext`, calls the configured `AccessPolicy`, and on a deny returns the reason as the tool's text result instead of invoking the method. An exception from the policy is caught and converted to a deny whose reason names the tool and says the policy failed, with the exception logged, not returned.

- [ ] **Step 4: Register `ALLOW_ALL` as the default bean** with `@ConditionalOnMissingBean`, and leave a commented skeleton `PgcAccessPolicy` next to it for the owner to replace, documented in the README as the place their business logic goes.

- [ ] **Step 5: Run the tests**

Run: `mvn -q test -Dtest=AccessPolicyTest`
Expected: PASS, 6 tests.

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: the AccessPolicy seam, defaulting to allow"
```

---

### Task 8: The client that prints the whole dance

**Files:**
- Create: `src/main/java/club/pgcgolf/springmcp/client/OAuthFlow.java`, `DiscoveryChain.java`, `PgcHttpMcpClient.java`, `HttpWireLog.java`
- Test: `src/test/java/club/pgcgolf/springmcp/client/DiscoveryChainTest.java`, `src/test/java/club/pgcgolf/springmcp/client/HttpWireLogTest.java`

**Interfaces:**
- Consumes: the running server's `401` challenge and metadata document from Task 6.
- Produces:
  - `record AuthorizationServer(String issuer, String authorizationEndpoint, String tokenEndpoint, String jwksUri)`
  - `DiscoveryChain.resolve(String mcpUrl) -> AuthorizationServer` — follows `401` → `resource_metadata` → RFC 8414 `/.well-known/oauth-authorization-server`, **falling back to OIDC `/.well-known/openid-configuration`** when the former returns a non-2xx. Cognito returns `400` for RFC 8414, so the fallback is the path that actually runs.
  - `OAuthFlow.authorize(AuthorizationServer, clientId, clientSecret, resource) -> String accessToken` — authorization code with PKCE, loopback listener on `127.0.0.1:8765/callback`.
  - `PgcHttpMcpClient.main(String[])` — runs discovery, the code exchange, prints the decoded token claims, then initialize / `tools/list` / `tools/call` over `HttpClientStreamableHttpTransport`, printing every message.
  - `HttpWireLog` — the same two-line rendering as phase 1b, written fresh here.

- [ ] **Step 1: Write the failing tests**

`DiscoveryChainTest`, against a stubbed HTTP server:
```java
@Test void resolve_readsTheResourceMetadataUrlOutOfTheChallenge()
@Test void resolve_usesRfc8414MetadataWhenTheServerPublishesIt()
@Test void resolve_fallsBackToOidcDiscoveryWhenRfc8414Returns400()   // the Cognito case
@Test void resolve_failsWithAClearMessageWhenNeitherIsAvailable()
```

`HttpWireLogTest`:
```java
@Test void describe_namesTheMethodAndIdOfARequest()
@Test void describe_marksASuccessfulResponseAsOk()
@Test void render_indentsTheJsonBodyUnderTheSummaryLine()
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -q test -Dtest='DiscoveryChainTest,HttpWireLogTest'`
Expected: FAIL — types not defined.

- [ ] **Step 3: Implement `DiscoveryChain`, `HttpWireLog`, `OAuthFlow` and `PgcHttpMcpClient`.** The client reads its client id and secret from environment variables, never from a file in the repo. The token is cached in the OS temp directory, not the working tree.

- [ ] **Step 4: Run the tests**

Run: `mvn -q test -Dtest='DiscoveryChainTest,HttpWireLogTest'`
Expected: PASS, 7 tests.

- [ ] **Step 5: Run the whole suite**

Run: `mvn test`
Expected: PASS, all tests from Tasks 2–8.

- [ ] **Step 6: Drive the running server by hand** and record the transcript in `docs/oauth-wire-log.md`: the `401` with its challenge, the two discovery documents, the decoded access token claims showing `aud`, and an authenticated `tools/call`.

- [ ] **Step 7: Commit**

```bash
git add src/ docs/oauth-wire-log.md
git commit -m "feat: a client that prints the discovery chain, the token exchange and the traffic"
```

---

### Task 9: Deploy behind nginx

**Files:**
- Create: `README.md`, `deploy/pgc-mcp.service`, `deploy/nginx-mcp.conf`, `docs/deploy.md`

**Interfaces:**
- Consumes: the shaded jar `target/pgc-spring-mcp.jar` from Task 2's build.

- [ ] **Step 1: Write `deploy/pgc-mcp.service`** — `User=ec2-user`, `ExecStart=/usr/bin/java -jar /opt/pgc-mcp/pgc-spring-mcp.jar`, `Restart=always`, `RestartSec=5`, `After=network-online.target`, `WantedBy=multi-user.target`. Environment file `/etc/pgc-mcp.env` for the Cognito issuer and resource id, referenced with `EnvironmentFile=` and **not** committed.

- [ ] **Step 2: Write `deploy/nginx-mcp.conf`** — two `location` blocks to add inside the existing `server` block for port 443: `/mcp/` and `/.well-known/oauth-protected-resource`, both `proxy_pass` to `http://127.0.0.1:8900`, with `proxy_http_version 1.1`, `proxy_set_header Host $host`, `proxy_set_header X-Forwarded-Proto $scheme`, `proxy_buffering off` and `proxy_read_timeout 1h`. Note in a comment that `location /` and `/.well-known/acme-challenge` must be left untouched.

- [ ] **Step 3: Write `docs/deploy.md`** — build, `scp` the jar to `/opt/pgc-mcp/`, install the unit, `systemctl enable --now pgc-mcp`, add the nginx locations, `nginx -t` **before** `systemctl reload nginx`, and the rollback: revert the nginx block and `systemctl stop pgc-mcp`. State that Tomcat is managed by systemd since 2026-11-01 and must not be touched by this deploy.

- [ ] **Step 4: Write `README.md`** — what this is, how it differs from PGCMcp, how to run the client, where `AccessPolicy` lives, and the three properties that matter (`pgc.mcp.resource-id`, the issuer URI, `pgc.api.base-url`).

- [ ] **Step 5: Verify the jar builds and starts**

Run: `mvn -q -DskipTests package && java -jar target/pgc-spring-mcp.jar --server.port=8901 &` then `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8901/mcp`
Expected: `401` — the server is up and refusing unauthenticated calls.

- [ ] **Step 6: Commit**

```bash
git add README.md deploy/ docs/deploy.md
git commit -m "docs: deployment unit, nginx locations and the runbook"
```

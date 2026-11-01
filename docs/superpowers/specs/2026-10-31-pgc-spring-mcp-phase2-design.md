# PGCSpringMCP — phase 2 design: Streamable HTTP and an OAuth 2.1 resource server

Date: 2026-10-31
Status: design, awaiting plan
Supersedes nothing. Narrows §4a phase 2 of
[the V1 design](2026-10-29-pgc-mcp-server-design.md).

## 1. Intent

The owner wants to understand, concretely, how an OAuth access token reaches an MCP server
and is validated there — with every step visible, the way phase 1b made the JSON-RPC traffic
visible by printing it.

**This is a learning exercise, not a product.** The owner stated it directly: *"this is just
for learning purpose. if any api i can learn how to use a access token that is good enough. i
just want to understand how it works."* They already know OAuth well and use the
authorization-code exchange flow at work, so the OAuth concepts are not what is new. What is
new is the MCP-specific layer: how a client that has never met a server discovers where to
authenticate, and where the token is checked.

This project also absorbs what the V1 design called "V2 — rewrite in Spring AI". Doing both at
once is deliberate: the Spring rewrite was going to teach job-applicable stack work, and the
auth work needs a framework that does JWT validation properly. One project, both lessons.

## 2. What already exists

- **PGCMcp** (phase 1 + 1b): bare MCP Java SDK 2.1.0, stdio transport, 11 read-only tools over
  the public pgcgolf.club API, 188 tests, plus a client that prints the wire in both directions.
  Merged; branch `feature-mcp-client-1b` is in PR #2.
- **PGCServer**: Spring Boot, deployed as `ROOT.war` into a standalone Tomcat on EC2.
  Authenticates browsers with Cognito `oauth2Login`. It is configured purely as an OAuth2
  **client**; there is no `spring.security.oauth2.resourceserver.*` property anywhere in the
  project, so phase 2 adds the first token-validating code in this codebase.
- **Cognito**: the live site's user pool is in `us-east-1`, issuer
  `https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XDeOxg5Ea`, app client requesting
  `scope=openid` only. **This pool is not used by this project** (D13) — a new throwaway pool is
  created instead. Facts verified against the live pool on 2026-10-31 and expected to hold for
  any Cognito pool: `/.well-known/openid-configuration` returns 200;
  `/.well-known/oauth-authorization-server` returns **400**; advertised
  `token_endpoint_auth_methods_supported` is `client_secret_basic, client_secret_post` — `none`
  is **not** advertised, so a strict client may refuse a public PKCE-only client even though
  Cognito supports one.

**PGCMcp is not modified by this project.** Phase 2 is a new, separate project, by the owner's
instruction: *"leave the pure java mcp server alone and create another project called
PGCSpringMCP."*

## 3. Goals

1. **Three** read-only tools, written fresh in Spring AI's own idiom, served over **stateless
   Streamable HTTP** at `https://pgcgolf.club/mcp`.
2. A real Cognito access token validated as a proper resource server would: signature against
   the pool's JWKS, `iss`, `aud`, and `token_use: access`.
3. `aud` populated via **RFC 8707 resource binding** — the `resource` parameter on
   `/oauth2/authorize` — so audience validation is the real mechanism and not a surrogate.
4. **RFC 9728 protected-resource metadata** plus a `401` carrying `WWW-Authenticate`, so an
   unauthenticated client can discover where to get a token. This is the part the owner has not
   met at work.
5. **One extension point** where the owner writes their own authorization logic, receiving the
   login from the validated token. Explicitly a skeleton for them to fill in.
6. A client **in this project** runs the code exchange and **prints the decoded claims**, so the
   `aud` configured in Cognito is visible arriving at the code that checks it.
7. Deployable to the existing EC2 box behind nginx, surviving a reboot.

## 4. Non-goals

- **Scope-based authorization.** The owner does this at work and asked for it to be skipped:
  *"do not worry about the token scope etc."* One scope may exist to make the token well-formed,
  but no tool differentiates on it.
- **Mapping a token to a player.** No `cognito_sub` column, no player lookup, no
  `get_my_score_history`. Considered and dropped — see §6, D10.
- **Reusing PGCMcp's tool layer, and porting all 11 tools.** Considered at length and dropped —
  see §6, D4. Three tools is enough for an auth exercise.
- **A working Claude Desktop / claude.ai connector.** Owner's choice: the hand-written client is
  the only client that must work. No authorization-server-metadata shim is built. See §6, D12.
- Write tools, admin tools, or any new API surface. Same as V1.
- A chat UI, or any change to PGCUI.
- Multi-user readiness. Exactly one account needs to work.
- Fixing the eight pre-existing unauthenticated write endpoints. Raised and explicitly deferred
  by the owner for this project: *"do not worry about the unprotected apis."* Recorded in §9.
- Any change to PGCMcp, PGCServer or PGCUI source. nginx config and a systemd unit on the
  server are in scope; application code in those repos is not.

## 5. Anti-goals — what would make this a failure even if it ran

- **Token passthrough, or trusting a caller's claim of identity.** If the server would accept
  "I am user X" from the caller, the exercise has failed. This is the single thing the design
  exists to get right.
- **Accepting a token not bound to this resource.** A token minted for PGCServer's app client,
  or for any other audience, must be rejected. If `aud` turns out to be absent, the server must
  **fail closed and say so**, never silently skip the check.
- **Leaking PII.** Same rule as V1, unconditional: `phone` and `email` are stripped at the tool
  boundary. Production currently holds 2 emails and 1 phone across 109 players, so the exposure
  is small, but the rule does not bend.
- **Taking the website down.** The MCP server shares a box with pgcgolf.club. It must not share
  Tomcat's JVM or lifecycle, and an nginx mistake must not break `location /`.
- **Secrets in the repo.** The Cognito client secret, and any token captured while testing, stay
  out of git and out of logs.

## 6. Decisions already made

| # | Decision | Why |
|---|---|---|
| D1 | A standalone **Spring Boot jar**, not a second WAR in the existing Tomcat | The deploy procedure shuts Tomcat down entirely; a WAR would couple every MCP restart to website downtime |
| D2 | `spring-ai-starter-mcp-server-webmvc` **2.0.2** (latest stable; 2.1.0-RC1 exists). It pulls Spring Boot **4.1.2** | Verified against Maven Central metadata on 2026-10-31. EC2 runs Corretto 21.0.4, which satisfies it |
| D3 | **Stateless** Streamable HTTP | Unlocked by D4. Where the 2026-07-28 protocol revision is heading: no protocol-level sessions, no session store, no sticky routing, any request may land on any instance. Simpler to reason about and the more job-relevant of the two |
| D4 | **Write three tools from scratch** in Spring AI's idiom. Do **not** reuse or copy PGCMcp's tool layer | Owner's call, and correct. Reuse was only ever testing V1's "transport swap, not a rewrite" claim, which mattered for a product this explicitly is not. Worse, bare-SDK `SyncToolSpecification` beans bypass Spring AI's `@Tool` programming model — so reuse would mean building a Spring AI project while learning no Spring AI. Eleven tools is set dressing for an auth exercise; three exercise schema generation and argument types. PGCMcp is therefore genuinely untouched, and its fat-jar packaging problem (see §10) becomes irrelevant |
| D5 | **Spring Security resource server** does the JWT validation | The owner chose Spring explicitly. Signature/`iss`/`aud` come from configuration; the interesting code is then the MCP-specific discovery layer, not re-implementing JWT parsing |
| D6 | Path-based: **`https://pgcgolf.club/mcp`**, not a subdomain | Owner's choice. No DNS change, no `certbot --expand`. Costs one extra nginx location for the well-known path |
| D7 | Bind **`127.0.0.1:8900`**; nginx proxies, with `proxy_buffering off` | Port 8900 is free (22, 80, 443, 3306, 8005, 8888, 33060 are in use). Loopback-only means an unauthenticated mistake is not instantly public. Without `proxy_buffering off`, SSE responses stall inside nginx |
| D8 | A **systemd unit** for the new service | Nothing on that box currently survives a reboot — there is no systemd unit even for Tomcat. Do not copy that pattern |
| D9 | Cognito setup is **documented as console steps**, not automated | No AWS CLI is installed locally, and the pool is shared with the live site. The owner performs these and reports back |
| D10 | Authorization is **one skeleton interface**, defaulting to allow | Owner's instruction: *"just leave a skeleton function so i can write a function which take the user login retrieved from jwt token and run some business logic to decide whether grant the access."* |
| D11 | A **deny from the policy is `isError` text in the tool result**, not a `401` | The caller is authenticated; they are simply not permitted. The `401` belongs to the layer above, for a missing or invalid token. Two distinct, separately observable failures |
| D12 | **The hand-written client is the only client that must work.** No Claude connector goal, no AS-metadata shim | Owner's call. Cognito returns **HTTP 400** for `/.well-known/oauth-authorization-server` (verified 2026-10-31) and publishes only OIDC discovery, and has no DCR — so a real connector was a genuine risk of a dead end. The client still performs the full discovery chain (RFC 8414 first, OIDC fallback), which is what real clients do, so the lesson survives |
| D13 | A **new, throwaway Cognito user pool**, not the live one | Owner's call. Zero risk to pgcgolf.club's login, free, deletable, and a test user with a known password makes the flow iterable without MFA friction. Real player identities are not needed here |
| D14 | The **three tools call the API with Spring's `RestClient`** | Idiomatic for this stack, and a copied `PgcApiClient` would be the duplication D4 just avoided. PII stripping still applies (see §5) |
| D15 | A **new sibling repo `PGCSpringMCP`**; the owner creates the GitHub remote | Owner's call, matching how PGCJavaMcp was set up. The `pgc-commit-push` skill covers only PGCServer and PGCUI, so this repo's workflow is set up separately |

### The extension point

```java
/** The caller, as the validated token describes them. */
public record TokenPrincipal(String login, String subject, Map<String, Object> claims) {}

public record AccessDecision(boolean allowed, String reason) {
    public static AccessDecision allow() { ... }
    public static AccessDecision deny(String reason) { ... }
}

public interface AccessPolicy {
    /** The token is already proven valid. Business logic goes here. */
    AccessDecision decide(TokenPrincipal caller, String toolName);

    /** The default, until the owner writes one. */
    AccessPolicy ALLOW_ALL = (caller, toolName) -> AccessDecision.allow();
}
```

`login` carries whichever claim the pool actually populates; `claims` is handed over raw so the
owner is not boxed in. The builder wires `ALLOW_ALL` as the default bean and leaves a commented
skeleton implementation for the owner to replace.

## 7. The flow this design makes observable

```
1. client calls https://pgcgolf.club/mcp with no token
   <-- 401
       WWW-Authenticate: Bearer resource_metadata="https://pgcgolf.club/.well-known/oauth-protected-resource/mcp"
2. client GETs that metadata                     -> which authorization server to trust
3. client GETs the Cognito AS metadata           -> authorize and token endpoints
4. authorization code + PKCE, with resource=<resource id>
5. code exchanged at the token endpoint          -> a JWT, printed and decoded
6. client retries with  Authorization: Bearer ...
7. server validates signature (JWKS), iss, aud, token_use  -> AccessPolicy -> tool runs
```

Steps 1–3 are the MCP-specific part and the reason a client can connect to a server it has
never met. Step 7 is where the two authorization layers separate.

## 8. Architecture

```
 Claude / the phase 1b client
        |  HTTPS
        v
 nginx :443  (Let's Encrypt, already present)
        |-- location /                                   -> Tomcat :8888   (the website, untouched)
        |-- location /mcp/                               -> :8900          (this project)
        |-- location /.well-known/oauth-protected-resource -> :8900        (RFC 9728 metadata)
        |   (and /.well-known/acme-challenge must keep working, for renewals)
        v
 PGCSpringMCP  (Spring Boot jar, 127.0.0.1:8900)
   Spring Security resource server  -> validates the JWT
   AccessPolicy                     -> the owner's business logic
   Spring AI MCP server (webmvc)    -> stateless Streamable HTTP
   three @Tool methods + RestClient -> written here, not reused
        |  HTTPS GET, read-only
        v
 pgcgolf.club/webapi/**
```

Note the MCP server calls the public API over HTTPS exactly as PGCMcp does — it holds no
database access and no business logic, per the V1 anti-goals.

## 9. Known hazards the builder must respect

- **`aud` is not present on a Cognito access token by default.** It appears only when the
  authorization request carried a `resource` parameter (RFC 8707). AWS's docs confirm Cognito
  supports this on authorization-code and implicit grants; it is **not** available on
  `client_credentials` M2M grants. Some third-party write-ups claim Cognito rejects the
  parameter outright — those are contradicted by AWS's current documentation, but the builder
  must verify empirically on this pool before the design is assumed sound, and must make the
  server fail closed if `aud` is missing.
- **`email` is an ID-token claim, not an access-token claim.** The existing Spring config uses
  `user-name-attribute = email`, which applies to the ID token. The access token carries `sub`,
  `username`, `client_id`, `scope` and `token_use`. The builder must report which claims this
  pool actually emits rather than assume, since `TokenPrincipal.login` depends on it.
- **SSE through nginx.** `proxy_buffering off` and a long `proxy_read_timeout` are required, or
  streamed responses hang with no error.
- **The website must not break.** nginx config changes are validated with `nginx -t` before
  reload, and `location /` is left alone.
- **Eight unauthenticated write endpoints exist** (`POST/PUT/DELETE /webapi/donations`,
  `POST/PUT/DELETE /webapi/rewards`, `POST /webapi/players/ghin`,
  `POST /webapi/event/create/playerscores`) and CSRF is disabled. The owner deferred these.
  Recorded here so the next reader does not mistake the silence for safety. Also:
  `/webapi/event/create/playerscores` takes an `@AuthenticationPrincipal` it never checks, and
  MySQL listens on `0.0.0.0:3306` — whether that is reachable depends on a security group this
  design's author could not inspect.
- **PII.** `phone` and `email` stay stripped at the tool boundary, inherited from V1. The three
  new tools must apply it themselves; there is no shared `Project.stripPii` here by design (D4).
- **PGCMcp's packaging, for the record.** Its shade plugin sets neither `shadedArtifactAttached`
  nor `shadedClassifierName` and uses `createDependencyReducedPom=false`, so `mvn install`
  publishes a fat jar as the main artifact whose pom still declares `mcp` 2.1.0 — a consumer
  would get the SDK twice. Verified 2026-10-31. Irrelevant now that D4 drops the dependency, but
  worth knowing before anyone tries to depend on PGCMcp in future.

## 10. Risks and their fallbacks

| Risk | Fallback |
|---|---|
| **Cognito will not emit `aud` even with the `resource` parameter.** This is the gate the whole design rests on | **Stop and report.** Do not substitute `client_id` silently — that would defeat the stated goal. The owner then chooses knowingly between a different authorization server and accepting the surrogate. Test this on the throwaway pool **before** any server code is written |
| Cognito does not advertise `none` for token-endpoint auth, so a public PKCE client may be refused by a strict client | Register the test app client as **confidential** (client secret) and keep the secret out of git. PKCE is still used. Recorded rather than worked around |
| The stateless starter's tool-registration bean types differ from the stateful one's | Follow the stateless reference docs; the three tools are small enough that adapting them costs little. This risk is small precisely because D4 dropped the reuse |
| Spring Boot 4.1.2 / Spring Security 7 API differences from the owner's experience, and from most tutorials | Follow the version's own reference docs rather than older recipes. Spring Boot 4 and Spring Security 7 are recent enough that most search results describe the previous major |
| ~~A reboot brings this service back while Tomcat stays down~~ | **Resolved 2026-11-01, outside this project.** `nginx` was `disabled` and Tomcat had no unit at all; both are now `enabled`, with `tomcat.service` ordered `After=mysqld.service`, and the switchover was verified live. The deploy skill was updated to drive Tomcat through systemd. Goal 7 is now achievable for the whole box |
| **The TLS certificate expires 2027-01-16 and nothing renews it** — `crond` is inactive, there is no certbot timer, and no cron entry anywhere references certbot. If it lapses, the MCP server's own HTTPS endpoint dies with the website | Not this project's bug, but this project depends on it. Must be fixed before phase 2 is deployed. Needs a systemd timer running `/opt/certbot/bin/certbot renew` |

## 11. Testing

Unit tests for the metadata document, the `WWW-Authenticate` challenge, `TokenPrincipal`
extraction, and `AccessPolicy` dispatch including a deny. Token validation is tested with
locally-signed JWTs against a test JWKS — correct `aud`, wrong `aud`, missing `aud`, wrong
`iss`, expired, bad signature — so the suite needs no network and no real Cognito. The three
tools are tested against a stubbed `RestClient`, including the PII strip.

One end-to-end check against the throwaway pool is performed by hand and recorded, as V1 did
against production.

**The `aud` gate comes first.** Before any server code, the builder obtains a token from the
throwaway pool with the `resource` parameter set and confirms `aud` is present and correct. If
it is not, the design stops here (§10).

The client in this project is the manual harness: it should print every step in §7. It carries
its own wire log — the phase 1b `WireLog`/`WireLogTransport` are **not** imported, since PGCMcp
stays untouched, and they are small enough that re-writing them for HTTP costs little.

## 12. Left to the builder

- Project layout, package names, class names beyond the extension point in §6.
- Whether the discovery metadata is a hand-written controller or generated by Spring Security's
  own support, whichever is less code on 4.1.2.
- How the client stores a token between runs while testing — a file in the scratchpad is fine;
  it must not be committed.
- Whether to keep a stdio entry point in this project. Not required; PGCMcp already has one.
- The exact Cognito resource-server identifier and scope name, proposed to the owner with the
  console steps.
- Which three tools, if the suggested `list_courses` (no arguments), `find_player` (string) and
  `get_player_profile` (number) are not the clearest set. The only requirement is that between
  them they cover no-argument, string-argument and numeric-argument schemas.
- Whether the client uses a loopback listener for the redirect or the owner pastes the code back.

### Where the paperwork goes

- This design: `PGCServer/docs/superpowers/specs/` (here), beside the V1 design.
- The implementation plan: `PGCServer/docs/superpowers/plans/`, as V1's was.
- Per-step audit entries: `PGCServer/PROGRESS.md`, continuing from STEP46.
- The code: a new sibling repo `PGCSpringMCP`, whose GitHub remote the owner creates (D15).
  `PGCMcp`'s PR #2 is **not** a prerequisite, since nothing here depends on it.

# SDD ledger — plan: docs/superpowers/plans/2026-11-15-pgc-chatbot.md
Spec: docs/superpowers/specs/2026-11-15-pgc-chatbot-design.md
Planning rulings (in plan): local dev calls live MCP; MCP 401 not retried -> signin; one MCP client per question.
Pre-flight:
- T2->T7: MemberTokenProvider.accessToken(auth,req,res) / SignInRequiredException consumed by controller — consistent.
- T3->T6/T7: ChatExchangeDto(question,answer,at) used by ChatAssistant history + /history — consistent.
- T4->T6/T7: PlayerRef(id,name) Optional — consistent.
- T5->T6/T7: McpGateway.open(token)->McpSession{tools(),call()}, McpUnauthorizedException — consistent.
- T6->T7: ChatAnswer(text,cost,in,out), ModelUnavailableException(cost) — consistent.
- T7->T8: refusal codes daily_limit|budget|signin|unavailable; 401 for signin — consistent.
- T1->T2: registration id "cognito" — matches existing properties.
Task 1: Ruling: ./mvnw is broken (.mvn/wrapper missing) — use system `mvn` for every test command — cost if wrong: none, same Maven goals.
Task 1: Ruling: suite runs with -Dspring.profiles.active=local; contextLoads fails on the default profile at BASE too (prod DB password vs local MySQL), pre-existing — cost if wrong: default-profile boot not covered by the suite.
Task 1: Ruling: Step 1 (owner ticks tools.read on website client) and Step 6 (manual login) moved to the start of Task 9 — both need the owner/browser; nothing before Task 9 depends on them — cost if wrong: a scope misconfiguration is found later.
Task 1: Ruling: added loginScope_includesMcpToolsRead (both profiles) instead of asserting scopes on the test's own fixture — the fixture assertion would test the test.
Task 1: complete (commits 85ba9cb..0595bd4, tests: mvn -q test -Dspring.profiles.active=local → 62/62 pass, exit 0)
Task 1: note — SecurityConfiguration.java is CRLF; edit with newline='' (amended b2109c7 → 0595bd4)
Task 2: complete (commits 0595bd4..0fb8593, tests: mvn -q test -Dspring.profiles.active=local → 66/66 pass, exit 0)
Task 3: Ruling: added ChatRepositoriesTest (@SpringBootTest + @Transactional, local profile, local MySQL) beyond the plan's Mockito-only tests — upsert, zero floor, DECIMAL add and member-scoped trim can only be proven by the database; suite already needs local MySQL — cost if wrong: suite needs chat.sql applied locally (done 2026-11-15).
Task 3: Ruling: refund_neverBelowZero / record_addsBigDecimalWithoutLoss proven in ChatRepositoriesTest (SQL), not as mocks; mutation-checked (floor, member scope, add) — each caught by its test.
Task 3: note — local MySQL is 9.7.0; VALUES() in ON DUPLICATE KEY UPDATE works there. No mysql CLI locally; DDL applied via JDBC (scratchpad ApplyDdl.java).
Task 3: complete (commits 0fb8593..abf76bb, tests: mvn -q test -Dspring.profiles.active=local → 82/82 pass, exit 0)
Task 4: Ruling: added nullUser_empty (resolve(null) → empty) — controller passes the principal straight through — cost if wrong: none.
Task 4: complete (commits abf76bb..81ef554, tests: mvn -q test -Dspring.profiles.active=local → 89/89 pass, exit 0)
Task 5: Ruling: hand-written MCP client (java.net.http + Jackson) instead of io.modelcontextprotocol.sdk:mcp — SDK 2.2.0 needs Reactor 3.9-RC/Jackson 2.22 vs Boot 3.3.5; owner declined Boot 4 (prod is Tomcat 10.1.30, Boot 4 needs Tomcat 11) — cost if wrong: own ~250 lines of protocol code; swap for SDK after a Boot upgrade.
Task 5: Ruling: added McpUnavailableException (non-401 HTTP errors, IO, timeouts, JSON-RPC errors) — plan only named McpUnauthorizedException; Task 7 maps it to `unavailable` — cost if wrong: none.
Task 5: Ruling: added tests beyond plan: initialize ordering, negotiated MCP-Protocol-Version header, SSE replies, 500/unreachable → unavailable, cache expiry, token never in messages.
Task 5: complete (commits 81ef554..579572d, tests: mvn -q test -Dspring.profiles.active=local → 102/102 pass, exit 0)
Task 6: Ruling: ModelGateway is a Conversation (start/send/addToolResults) holding the SDK transcript, not ModelRequest/ModelReply — the model's own content incl. thinking blocks must be replayed unchanged — cost if wrong: none for callers.
Task 6: Ruling: ChatUsage (caller-owned meter) instead of ChatAnswer(cost)/ModelUnavailableException(cost) — spend before ANY failure (model or MCP) stays recorded — Task 7 records usage in finally.
Task 6: Ruling: max_tokens 2048 (plan 1024) + effort LOW — Sonnet 5.5 thinks by default; 1024 risked truncation; low effort is the doc's chat recommendation — cost if wrong: a few cents/month more.
Task 6: Ruling: today's date in the system prompt — "this season" needs it.
Task 6: Ruling: refusal → fixed polite sentence, no server-side `fallbacks` (claude-api skill default is to enable it on Sonnet 5.5 via the beta endpoint) — needs beta request types; club Q&A refusal is acceptable to show as a decline — RAISE WITH OWNER.
Task 6: Ruling: model stays claude-sonnet-5-5 as approved in the design (claude-api skill default is claude-opus-5-5 at 2x price) — owner approved Sonnet with the $10 budget — RAISE WITH OWNER as a reminder.
Task 6: note — anthropic-java 3.2.0 sends the key as Authorization: Bearer (not x-api-key). SDK wants Jackson 2.19.4; Boot pins 2.17.2 — loopback test proves request/response round-trip works on 2.17.2.
Task 6: note — ChatConfigTest written alongside the code (not watched RED first); mutation (remove containsKey) made unpricedModel_failsAtStartup fail.
Task 6: complete (commits 579572d..8d93758, tests: mvn -q test -Dspring.profiles.active=local → 126/126 pass, exit 0)
Task 7: Ruling: tests run on JDK 21 (JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home) — default mvn JDK is 25 and Mockito/Byte Buddy cannot mock classes on 25; prod is Java 21 — cost if wrong: none; all earlier task runs used 25 (passed) and are re-run on 21 here.
Task 7: Ruling: @SpringBootTest+@AutoConfigureMockMvc (local profile) instead of @WebMvcTest — the slice pulls ShiyuanApplication's restTemplate bean (needs RestTemplateBuilder), same as Task 3.
Task 7: Ruling: added formPost_415, sessionIsClosedAfterTheQuestion, and 'invalid' refusal body on 400.
Task 7: note — history.recent(sub) is passed to the assistant (the last 3 exchanges as context).
Task 7: complete (commits 8d93758..ca8ae2e, tests: mvn -q test -Dspring.profiles.active=local → 140/140 pass on JDK 21, exit 0)
Task 8: Ruling: PGCUI baseline `ng test` fails 27/28 before any change (generated specs without providers) — ran only the chat + menubar specs (--include); rewrote the broken menubar spec — cost if wrong: other UI regressions not covered by tests (prod build passes).
Task 8: Ruling: marked pinned 12.0.2 (TS 5.1 compatible); Markdown bound via [innerHTML] with Angular's sanitizer — mutation (bypassSecurityTrustHtml) made the XSS spec fail.
Task 8: Ruling: added specs: no history load while closed, trimmed single send, blank not sent, other refusals show message, close emits; ChatService maps non-401 errors to unavailable and passes 200 refusal bodies through.
Task 8: note — PGCUI commit 8d3f9e96 (base b5338215); prod build OK (output to scratch, dist/ untouched); no chat warnings.
Task 8: complete (PGCUI commits b5338215..8d3f9e96, tests: ng test --include chat.service/chat-panel/menubar specs → 19/19 SUCCESS)
Final review: fresh reviewer (fable), verdict "ready for local e2e testing: with fixes"; 0 critical, 4 important, 10 minor.
Final: fixed slow-API deadline (Important 2) — deadlinePassed_noFurtherSend_unavailable_costKept RED→GREEN; SDK timeout 20 s, retries 0, deadline 35 s (nginx default 60 s on the site route, checked read-only) — suite 144/144.
Final: fixed signin cause logging + tokens actually in HTTP session (Important 3) — signin_logsItsCause_* and tokensAreKeptInTheHttpSession RED→GREEN — suite 144/144.
Final: note — " 2" duplicate classes in target/ broke the owner's spring-boot:run: concurrent builds and/or iCloud Documents sync; mvn clean fixed it.
Final: fixed IME Enter (Important 1) — 'Enter that confirms a Chinese IME candidate does not send' RED→GREEN, UI specs 21/21 (PGCUI 6eed4048)
Final: fixed rollout gate (Important 4) — plan Task 10 Step 0 precondition (tools.read ticked on website client before deploy); Task 9 Step 4 now reads the signin cause. Docs only, no test.
Final: minor (deferred): unexpected exceptions (DB hiccup) skip the daily refund and return Spring's 500.
Final: minor (deferred): tool names not in the per-question log line (spec §5.9 asks for them).
Final: minor (deferred): fixed sentences (cap, refusal, limits) are English-only, even for Chinese questions.
Final: minor (deferred): a max_tokens stop with no tool calls returns silently truncated text.
Final: minor (deferred): no prompt caching on tool definitions/system prompt.
Final: minor (deferred): MySQL VALUES() in ON DUPLICATE KEY UPDATE is deprecated (works on 9.7).
Final: minor (deferred): panel loads history once per page; a 401 on history shows an empty panel.
Final: minor (deferred): Markdown http(s) links from tool data are clickable.
Final: minor (deferred): panel z-index 1000 equals the CDK overlay container's.
Final: minor (deferred): keep the daily-count read inside the @Transactional (note only).
Task 9: complete (owner-run, local e2e 2026-11-16: six questions incl. Chinese, general golf, off-topic decline, history of 3; refresh check after >1 h passed; log lines confirmed token/email/text-free. Local spend: 3 questions = $0.042912, 19006 in / 490 out tokens ≈ 1.4 ¢/question)
Task 10: Step 0 precondition met — local sign-in via website client 3j0ifum6… with tools.read succeeded in Task 9.
Task 10: complete (prod deploy 2026-11-16 ROOT.war sha 4b661417…, archive ROOT.war.11162026_0037; owner verified in production; 10/10 outcome=answered; spend 2026-11 $0.217832, 87706 in / 4242 out)

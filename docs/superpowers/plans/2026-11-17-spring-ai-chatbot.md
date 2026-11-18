# PGC Club Assistant, Spring AI edition — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A second club-assistant backend written with Spring AI 1.1.8 (`POST /webapi/chat/spring`), plus an **S** button in PGCUI that opens it. It shares history, limits and budget with the current bot.

**Architecture:** New package `com.shiyuan.chat.springai`. Three classes do the work:
- `SpringAiModels`: a lazily built `AnthropicChatModel` with no retries and a 20 s timeout;
- `McpToolsSession`: a per-question MCP Java SDK client carrying the member's Bearer token, with its tools exposed as `SyncMcpToolCallback`s;
- `SpringAiChatAssistant`: a user-controlled tool loop over `ChatModel` and `ToolCallingManager`.

`SpringAiChatController` copies `ChatController`'s flow. The UI reuses `ChatPanelComponent` with a `variant` input.

**Tech Stack:** Spring Boot 3.5.16, Spring AI 1.1.8 (`spring-ai-bom`, `spring-ai-anthropic`, `spring-ai-mcp`), MCP Java SDK 0.18.3, JUnit 5 with JDK `HttpServer` fakes, and Angular 16 with Karma.

**Spec:** `docs/superpowers/specs/2026-11-17-spring-ai-chatbot-design.md`. Behaviour comes from `docs/superpowers/specs/2026-11-15-pgc-chatbot-design.md`.

## Global Constraints

- Spring AI **1.1.8**, imported as the `spring-ai-bom` in `<dependencyManagement>`. **No Spring AI starters.**
- The current bot is not changed in behaviour. The only edits to its classes are visibility changes: `ChatUsage.add` becomes public, `ChatAssistant.TOO_MANY_LOOKUPS`/`CANNOT_HELP` become public, and the test-only `FakeMcpServer` becomes public.
- The model is `pgc.chat.model` with `max_tokens` = `pgc.chat.max-tokens` (2048).
- At most `pgc.chat.max-tool-calls` (5) tool calls per question, and a **35 s** deadline per question.
- **One** attempt per model call (no retry); a **20 s** read timeout per model call.
- The API key is `pgc.chat.anthropic-api-key`. If it's blank, the app still starts; the question fails with `ModelUnavailableException("Claude API key is not configured")`.
- The MCP URL is `pgc.chat.mcp-url`, default `https://pgcgolf.club/mcp`. Every MCP request carries `Authorization: Bearer <member token>`.
- Tokens, questions, answers and emails are never logged or put in exception messages.
- Tests run on JDK 21: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home`. Use system `mvn` and `-Dspring.profiles.active=local`.
- PGCServer `PROGRESS.md` gets a STEP entry at the end. Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Branch: `feature-boot-3.5` (PGCServer). PGCUI: a new branch `feature-spring-ai-chat` from `feature-07182026`.

## Review Focus

1. **A thinking block from Claude in a tool round** must go back unchanged on the next request, or the API rejects it. Pinned in Task 3: `thinkingBlock_isReplayedOnNextRequest`.
2. **The MCP server returns 401 part-way through a question,** on `tools/call` rather than on `initialize`. The member should get "sign in again", not "unavailable". Pinned in Task 2: `callAfter401_isUnauthorized`.
3. **A blank API key in production** must not break start-up, and must not count spend. Pinned in Task 1 (`blankKey_contextStarts_modelThrows`) and Task 3 (`blankKey_noUsageRecorded`).
4. **The retry template** must never retry on a 529 or 500, or one question could pass the 35 s deadline and cost several calls. Pinned in Task 3: `modelError_oneAttempt_modelUnavailable`.
5. **Both buttons clicked one after the other** must leave only one panel open. Pinned in Task 5: `sButton_closesSdkPanel`.

---

### Task 1: Dependencies, visibility, and the lazy model

**Files:**
- Modify: `pom.xml` (add the `spring-ai-bom` 1.1.8 import plus the `spring-ai-anthropic` and `spring-ai-mcp` dependencies)
- Modify: `src/main/java/com/shiyuan/chat/ChatUsage.java` (`add` → public), `ChatAssistant.java` (the two constants → public)
- Create: `src/main/java/com/shiyuan/chat/springai/SpringAiModels.java`, `SpringAiChatConfig.java`
- Test: `src/test/java/com/shiyuan/chat/springai/SpringAiModelsTest.java`

**Interfaces:**
- Produces: `public final class SpringAiModels implements Supplier<ChatModel>`, with constructor `SpringAiModels(String apiKey, String baseUrl, String model, long maxTokens)`.
  - `get()` builds an `AnthropicChatModel` once and caches it (thread-safe).
  - The model's default options are `AnthropicChatOptions` with model, maxTokens and `internalToolExecutionEnabled(false)`.
  - It uses `RetryTemplate.builder().maxAttempts(1).build()`.
  - `AnthropicApi` gets the base URL (if not blank) and a `RestClient.Builder` whose request factory has a 20 s read timeout and a 10 s connect timeout.
  - `get()` throws `ModelUnavailableException("Claude API key is not configured")` when the key is blank.
- `SpringAiChatConfig` (`@Configuration`) defines a `SpringAiModels` bean and, in Task 3, the assistant bean.

- [ ] **Step 1: Failing tests.**
  - `blankKey_contextStarts_modelThrows`: `new SpringAiModels("", null, "claude-sonnet-5-5", 2048).get()` throws `ModelUnavailableException` whose message is `"Claude API key is not configured"`.
  - `key_buildsAnthropicModel_withToolExecutionOff`: `get()` returns an `AnthropicChatModel`. Its `getDefaultOptions()` is an `AnthropicChatOptions` with model `claude-sonnet-5-5`, maxTokens 2048 and `getInternalToolExecutionEnabled() == false`. Calling `get()` twice returns the same instance.
- [ ] **Step 2:** Run `mvn -q test -Dtest=SpringAiModelsTest -Dspring.profiles.active=local`. Expected: a compile failure (no classes yet).
- [ ] **Step 3:** Add the pom entries and implement `SpringAiModels` and `SpringAiChatConfig`. The bean reads `ChatProperties` (key, base URL, model, max tokens).
- [ ] **Step 4:** Run `SpringAiModelsTest`, then the whole suite. Expected: PASS, and the suite stays at 144 + 2 with `contextLoads` green. That proves a blank local key doesn't break start-up and the app stays a servlet app.
- [ ] **Step 5:** Commit: `feat(chat-springai): Spring AI 1.1.8 deps and lazy Anthropic model`.

### Task 2: `McpToolsSession` — Spring AI tools with the member's token

**Files:**
- Modify: `src/test/java/com/shiyuan/chat/FakeMcpServer.java` (make the class, its constructor, `seen`, `Seen`, `status`, `url()`, `count()` and `close()` public; add `public volatile int toolsCallStatus = 200`, which applies only to `tools/call`)
- Create: `src/main/java/com/shiyuan/chat/springai/McpToolsSession.java`
- Test: `src/test/java/com/shiyuan/chat/springai/McpToolsSessionTest.java`

**Interfaces:**
- Produces: `public final class McpToolsSession implements AutoCloseable`.
  - `static McpToolsSession open(String mcpUrl, String accessToken)` builds a `McpClient.sync(HttpClientStreamableHttpTransport.builder(base).endpoint(path).customizeRequest(b -> b.header("Authorization", "Bearer " + token)).build())` with a 15 s request timeout, then calls `initialize()`.
  - `List<ToolCallback> toolCallbacks()` returns one `SyncMcpToolCallback` per tool from `listTools()`.
  - `RuntimeException translate(Throwable t)` returns `McpUnauthorizedException` if the cause chain shows HTTP 401, otherwise `McpUnavailableException`. Messages hold method and status only, never the token. Used by `open`, `toolCallbacks`, and in Task 3 for tool-call failures.
  - `close()` calls `closeGracefully()`.

- [ ] **Step 1: Failing tests** (each uses `new FakeMcpServer()`):
  - `everyRequest_carriesBearer`: open with token `"tok-123"` and call `toolCallbacks()`. Every entry in `seen` has authorization `"Bearer tok-123"`, and `count("initialize") == 1`.
  - `toolCallbacks_wrapMcpTools`: there is one callback, and its `getToolDefinition().name()` is `"get_standings"`. `call("{\"limit\":5}")` returns text containing `totalPlayers`.
  - `initialize401_isUnauthorized`: with `status = 401`, `open` throws `McpUnauthorizedException`.
  - `initialize500_isUnavailable`: with `status = 500`, `open` throws `McpUnavailableException`.
  - `callAfter401_isUnauthorized`: with `toolsCallStatus = 401`, a callback's `call(...)` throws, and `translate(thrown)` is a `McpUnauthorizedException`.
  - `exceptionMessages_neverContainToken`: in each failing case above, `getMessage()` doesn't contain `"tok-123"`.
- [ ] **Step 2:** Run `-Dtest=McpToolsSessionTest`. Expected: FAIL (the class is missing).
- [ ] **Step 3:** Implement. Find out how SDK 0.18.3 reports an HTTP status (exception type and message) by reading what the 401 and 500 tests throw, and map on that. Record the finding as a ruling in the ledger.
- [ ] **Step 4:** Run the test, then the suite. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(chat-springai): per-member MCP client exposing Spring AI tool callbacks`.

### Task 3: `SpringAiChatAssistant` — the tool loop

**Files:**
- Create: `src/main/java/com/shiyuan/chat/springai/SpringAiChatAssistant.java`
- Modify: `SpringAiChatConfig.java` (assistant bean: `ChatPricing` built as in `ChatConfig`, and `@Value("${pgc.chat.mcp-url:https://pgcgolf.club/mcp}")`)
- Create: `src/test/java/com/shiyuan/chat/springai/FakeAnthropicServer.java` (a loopback `/v1/messages` server that records request JSON and the `x-api-key` header and replies from a queue; same pattern as `AnthropicModelGatewayTest`)
- Test: `src/test/java/com/shiyuan/chat/springai/SpringAiChatAssistantTest.java`

**Interfaces:**
- Consumes: `SpringAiModels` (Task 1), `McpToolsSession` (Task 2), and `ChatPrompt.system`, `ChatPricing.cost`, `ChatUsage.add`, `ChatAssistant.TOO_MANY_LOOKUPS`/`CANNOT_HELP`.
- Produces: `public class SpringAiChatAssistant`, with constructor `(Supplier<ChatModel> models, ChatPricing pricing, String modelId, int maxToolCalls, String mcpUrl, Clock clock, Duration deadline)`. The bean uses `Duration.ofSeconds(35)`.
  - `public String answer(String question, Optional<PlayerRef> me, List<ChatExchangeDto> history, String accessToken, ChatUsage usage, LocalDate today)`.
  - It throws `ModelUnavailableException`, `McpUnauthorizedException` or `McpUnavailableException`, with the same meanings as `ChatAssistant`.
- The loop, as an algorithm:
  1. Messages are `SystemMessage(ChatPrompt.system(me, today))`, then a `UserMessage`/`AssistantMessage` pair per history item (oldest first), then `UserMessage(question)`.
  2. Options: copy the default options, setting `toolCallbacks(session.toolCallbacks())` and `internalToolExecutionEnabled(false)`.
  3. Loop:
     - check the deadline;
     - `response = model.call(prompt)`, wrapping any `RuntimeException` that isn't already ours as `ModelUnavailableException("model call failed: " + simpleName)`;
     - add the usage (prompt and completion tokens, priced);
     - if the finish reason is `refusal`, return `CANNOT_HELP`;
     - if there are no tool calls, return the trimmed text, or `CANNOT_HELP` if it's empty;
     - if the finish reason is `max_tokens`, or the running count plus this round's tool calls would exceed `maxToolCalls`, return `TOO_MANY_LOOKUPS`;
     - `result = toolCallingManager.executeToolCalls(prompt, response)`, with failures passed through `McpToolsSession.translate`;
     - `prompt = new Prompt(result.conversationHistory(), options)`.
  4. The session is opened per call to `answer` and closed in `finally`.

- [ ] **Step 1: Failing tests** (`FakeAnthropicServer` + `FakeMcpServer`; `ChatPricing` with sonnet at $2/$10; `Clock.fixed` for the deadline cases):
  - `firstRequest_hasModelMaxTokensSystemHistoryQuestionTools`: the first request JSON has `model` = `claude-sonnet-5-5` and `max_tokens` = 2048. Its `system` contains the `ChatPrompt` text. `messages` is `[user q1, assistant a1, user "Where am I?"]`. `tools[0].name` is `get_standings`. The `x-api-key` header is the configured key.
  - `toolRound_thenAnswer`: the replies are a tool_use reply then a text reply. The result is `"You're #7."`. MCP `tools/call` is seen once with `Bearer tok-123`. The second request ends with a user message holding a `tool_result` whose `tool_use_id` is `toolu_1`.
  - `thinkingBlock_isReplayedOnNextRequest`: the tool_use reply also has a thinking block `{"type":"thinking","thinking":"","signature":"sig-abc"}`. The second request's assistant message includes that block with signature `sig-abc`. (Risk in spec §8. If this fails, rule and record it before going on.)
  - `toolError_goesBackToModel`: tool `broken` returns `isError`. The question still ends with the text reply, and the second request's tool_result text contains `Event 999 does not exist.`.
  - `sixthToolCall_tooManyLookups`: six tool_use replies with `maxToolCalls` 5 give `ChatAssistant.TOO_MANY_LOOKUPS`.
  - `refusal_cannotHelp`: stop_reason `refusal` gives `CANNOT_HELP`. A text reply of `""` also gives `CANNOT_HELP`.
  - `maxTokensWithToolCall_tooManyLookups`: stop_reason `max_tokens` with a tool_use block gives `TOO_MANY_LOOKUPS`, and no `tools/call` is seen.
  - `deadlinePassed_modelUnavailable`: a clock already past the deadline at the second check throws `ModelUnavailableException`.
  - `usage_pricedPerCall`: two calls with usage (1200/80) and (1500/20) give `usage.inputTokens()==2700`, `outputTokens()==100` and `cost()` = `0.006400` ((2700×2 + 100×10)/1e6).
  - `modelError_oneAttempt_modelUnavailable`: with status 529, the call throws `ModelUnavailableException` and the fake sees exactly **1** request.
  - `blankKey_noUsageRecorded`: a blank key throws `ModelUnavailableException`, `usage.inputTokens()==0`, and no MCP request is seen.
  - `mcp401_unauthorized`: MCP `status = 401` throws `McpUnauthorizedException`.
- [ ] **Step 2:** Run `-Dtest=SpringAiChatAssistantTest`. Expected: FAIL (the class is missing).
- [ ] **Step 3:** Implement the loop and the config bean. Use `DefaultToolCallingManager.builder().build()`. Read the finish reason from `response.getResult().getMetadata().getFinishReason()`, compared case-insensitively.
- [ ] **Step 4:** Run the test, then the suite. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(chat-springai): Spring AI tool loop with limits, deadline and pricing`.

### Task 4: `SpringAiChatController` — `POST /webapi/chat/spring`

**Files:**
- Create: `src/main/java/com/shiyuan/controller/SpringAiChatController.java`
- Test: `src/test/java/com/shiyuan/controller/SpringAiChatControllerTest.java` (same setup as `ChatControllerTest`: `@SpringBootTest @AutoConfigureMockMvc`, `@MockBean` for `ChatBudget`, `ChatLimits`, `PlayerResolver`, `MemberTokenProvider`, `SpringAiChatAssistant` and `ChatHistoryService`)

**Interfaces:**
- Consumes: `SpringAiChatAssistant.answer(...)` (Task 3) and the shared beans.
- Produces: `POST /webapi/chat/spring`, which consumes and produces JSON, takes `ChatRequest` and returns `ChatResponse`. The flow and refusal texts are identical to `ChatController.ask`. The log line is `chat impl=springai sub={} outcome={} in={} out={} cost={}`.

- [ ] **Step 1: Failing tests.** Copy the matching `ChatControllerTest` cases, retargeted at `/webapi/chat/spring`:
  - `anonymous_post_401`, `textPlain_415`, `blank_400`, `tooLong_400`;
  - `budgetExhausted_noModelCall` (the assistant is never called);
  - `dailyLimit_refusal`;
  - `answered_savesHistory_recordsSpend`: `limits.tryConsume(sub)`, `history.save(sub, q, answer)` and `budget.record(cost, in, out)` are each called once;
  - `modelUnavailable_refunds`;
  - `mcpUnauthorized_401_refunds`;
  - `signInRequired_401_refunds`.
- [ ] **Step 2:** Run `-Dtest=SpringAiChatControllerTest`. Expected: FAIL (404s, because the controller is missing).
- [ ] **Step 3:** Implement by copying `ChatController.ask`, swapping `McpGateway`/`ChatAssistant` for `SpringAiChatAssistant.answer(question, me, history.recent(sub), accessToken, usage, today)`. No security change is needed, because `/webapi/chat/**` is already authenticated.
- [ ] **Step 4:** Run the test, then the whole suite. Expected: all green.
- [ ] **Step 5:** Commit: `feat(chat-springai): POST /webapi/chat/spring`.

### Task 5: PGCUI — the S button and panel variant

**Files** (PGCUI, branch `feature-spring-ai-chat`):
- Modify: `src/app/services/chat.service.ts` (+ spec)
- Modify: `src/app/component/chat-panel/chat-panel.component.{ts,html}` (+ spec)
- Modify: `src/app/component/menubar/menubar.component.{ts,html,css}` (+ spec)

**Interfaces:**
- `ChatService.ask(question: string, variant: ChatVariant = 'sdk')`, where `export type ChatVariant = 'sdk' | 'springai'`. It posts to `/webapi/chat` for `'sdk'` and to `/webapi/chat/spring` for `'springai'`. `history()` is unchanged.
- `ChatPanelComponent` gets `@Input() variant: ChatVariant = 'sdk'`. The title is `Club Assistant` or `Club Assistant (Spring AI)`, and it passes the variant to `ask`.
- In the menubar, `chatOpen: ChatVariant | null = null`. There are two buttons:
  - the existing one, `aria-label="Open club assistant"`, toggles `'sdk'`;
  - the new one, `aria-label="Open club assistant (Spring AI)"` with `data-test="chat-springai"`, toggles `'springai'` and shows a circled **S** (a styled `<span class="chat-s-badge">S</span>`).
  - There is one `<app-chat-panel>` with `[open]="chatOpen !== null"` and `[variant]="chatOpen ?? 'sdk'"`, and `(closed)` sets `chatOpen = null`.
  - Both buttons sit inside the existing `*ngIf="authStatus.loggedIn"` block.

- [ ] **Step 1: Failing specs:**
  - `ask_springai_postsToSpringEndpoint` (`HttpTestingController` expects `POST .../webapi/chat/spring`), with `ask_default_postsToChat` kept;
  - `title_showsSpringAi_whenVariantSpringai`, and `send_passesVariant` (spy on `ask`, which is called with `'springai'`);
  - `sButton_hiddenWhenLoggedOut`, `sButton_opensSpringAiPanel`, `sButton_closesSdkPanel` (click 💬 then S: one `app-chat-panel`, variant `springai`), and `clickingSTwice_closes`.
- [ ] **Step 2:** Run `npx ng test --watch=false --browsers=ChromeHeadless --include='src/app/services/chat.service.spec.ts' --include='src/app/component/chat-panel/chat-panel.component.spec.ts' --include='src/app/component/menubar/menubar.component.spec.ts'`. Expected: the new specs FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same command (all pass), then `npm run build` (exit 0).
- [ ] **Step 5:** Commit in PGCUI, excluding `dist/`: `feat(chat): Spring AI assistant button (S) and panel variant`.

### Task 6: Real end-to-end run and record

- [ ] **Step 1:** Bundle the UI into `static/` (deploy skill steps 1–2) and run PGCServer locally with the `local` profile and `PGC_CHAT_ANTHROPIC_API_KEY`. The owner logs in, opens **S**, and asks a question that needs a tool (e.g. "who is leading the standings?"). Expected:
  - an answer;
  - a log line `chat impl=springai … outcome=answered`;
  - the same exchange visible in the 💬 panel's history.
  If the real API rejects the second request because of thinking blocks (spec §8), rule on a fix with a failing test first.
- [ ] **Step 2:** Append STEP64 to `PROGRESS.md` (what was built, rulings, the result of the real run) and commit: `docs: STEP64 — Spring AI chatbot`.

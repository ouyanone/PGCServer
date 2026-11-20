# PGC Club Assistant, Spring AI edition — Design

**Date:** 2026-11-17 · **Owner:** Yan Ou · **Status:** approved in chat ("perfect. go ahead")

Builds on the existing chatbot design: `2026-11-15-pgc-chatbot-design.md` (called "the
chatbot spec" below). Everything that spec says about behaviour applies here unless this
document says otherwise.

## 1. Intent

A second, independent implementation of the club assistant, written with **Spring AI 1.1.x**,
**for learning Spring AI**. The owner does not want to compare the two bots. The point is to
see how Spring AI does the same jobs as the hand-written code: the model call, the MCP
client, and the tool loop.

## 2. Goals

- A member sees **the same behaviour** as the current bot:
  - club-only answers; general golf answered briefly;
  - replies in the question's language;
  - "me" works;
  - 30 questions a day; a $10 monthly budget; memory of the last 3 questions.
- The Spring AI code uses Spring AI's own building blocks for the three jobs above, not thin
  wrappers around the existing hand-written classes.
- In the UI, a second button with the letter **S** opens the Spring AI bot.

## 3. Non-goals

- Any comparison feature (side-by-side answers, cost reports by bot, A/B).
- Changing the current bot. `ChatController`, `ChatAssistant`, `AnthropicModelGateway` and
  `McpGateway` stay as they are.
- MCP protocol 2026-07-28. Spring AI 1.1.8 ships MCP Java SDK 0.18.3, which speaks
  2025-11-25. That matches the MCP server.
- RAG, streaming, new tools, and anything else not in the current bot.

## 4. Anti-goals

- Doubling the spend: the two bots must share **one** budget and **one** daily count.
- The member's token leaving PGCServer, or being logged.
- Breaking the current bot or production start-up. In particular, a missing API key must not
  stop the app from starting, which matches the current bot.

## 5. Decisions

### 5.1 Shared state (owner: "the two bot share the same history")

Both bots read and write the same tables (`chat_exchange`, `chat_daily_count`,
`chat_monthly_spend`) through the existing `ChatHistoryService`, `ChatLimits` and
`ChatBudget`. A question asked in one bot shows up in the other bot's history.

### 5.2 Reused unchanged

`ChatPrompt` (same rules text), `PlayerResolver` ("me"), `MemberTokenProvider` (the member's
access token, with refresh), `ChatLimits`, `ChatBudget`, `ChatHistoryService`, `ChatPricing`,
`ChatUsage`, `ChatProperties`, the exception types, and the fixed sentences
(`TOO_MANY_LOOKUPS`, `CANNOT_HELP`).

### 5.3 New Spring AI code (builder's call, outlined to the owner)

New package `com.shiyuan.chat.springai`.

- **Model:** Spring AI's `AnthropicChatModel` over `AnthropicApi`, from `spring-ai-anthropic`
  1.1.8, with versions managed by `spring-ai-bom` 1.1.8.
  - **Beans are built by hand in a `@Configuration`, not by the Spring AI starter** (builder's
    call). The starter's autoconfiguration requires `spring.ai.anthropic.api-key` at start-up,
    which would break local runs and tests without a key (anti-goal 4). It also adds
    chat-memory beans we don't use.
  - The key comes from the existing `pgc.chat.anthropic-api-key`. If it's blank, the bot
    answers "unavailable" when asked, as the current bot does.
  - Options: the model from `pgc.chat.model`; `max_tokens` from `pgc.chat.max-tokens` (2048);
    **no retries** (the retry template allows 1 attempt); a 20 s read timeout on the HTTP
    client.
  - **Known difference (owner accepted):** Spring AI 1.1.8 cannot send `output_config.effort`,
    so this bot runs at Claude's default effort. Answers may be longer and cost a little more.
    No workaround.
- **MCP client:** for each question, a `McpSyncClient` from the MCP Java SDK 0.18.3 (brought in
  by `spring-ai-mcp` 1.1.8), on `HttpClientStreamableHttpTransport`.
  - The transport adds `Authorization: Bearer <member token>` through `customizeRequest`, and
    points at `pgc.chat.mcp-url`.
  - The client is opened, used and closed within one question.
  - Spring AI's MCP client starter is **not** used: it connects once at start-up with one
    identity, and every member needs their own token.
- **Tools:** each MCP tool from `listTools()` becomes a Spring AI `SyncMcpToolCallback`.
- **Tool loop:** Spring AI's documented "user-controlled tool execution":
  - `internalToolExecutionEnabled(false)` on the chat options;
  - the loop calls `ChatModel.call(prompt)`; while the response has tool calls, it runs
    `ToolCallingManager.executeToolCalls(prompt, response)` and continues with the returned
    conversation history.
  - The loop is ours so that the existing limits still hold: at most `pgc.chat.max-tool-calls`
    (5) tool calls per question, and a 35 s deadline per question. Exceeding either gives the
    same outcomes as the current bot.
  - **Why not `ChatClient`** (builder's call): `ChatClient` would run the tool loop internally,
    with no tool-call limit or deadline. The loop uses `ChatModel` directly, which is the
    lower-level API that `ChatClient` sits on.
- **History:** the 3 saved exchanges become Spring AI `UserMessage`/`AssistantMessage` pairs
  before the new question. `ChatPrompt.system(...)` becomes the `SystemMessage`.
- **Cost:** for each model call, token usage from `ChatResponse.getMetadata().getUsage()` is
  priced with `ChatPricing` and added to `ChatUsage`, as the current bot does.
- **Errors** map onto the existing exceptions, so outcomes, refunds and logging behave the same:
  - the MCP server rejects the token (HTTP 401) → `McpUnauthorizedException` → sign in again;
  - any other MCP failure → `McpUnavailableException`;
  - a model failure or timeout → `ModelUnavailableException`;
  - a tool that returns an error result goes back to Claude as a tool error, without failing
    the question. This is Spring AI's default behaviour.
- A Claude refusal (`stop_reason` "refusal") or an empty answer → `CANNOT_HELP`.

### 5.4 Endpoint

- `POST /webapi/chat/spring`: same request and response JSON as `POST /webapi/chat`, JSON only,
  and authenticated, since it's already covered by `/webapi/chat/**`. It goes through the same
  checks in the same order: question length, then budget, daily limit, the answer, saving
  history, recording spend, and refunds on failure.
- History uses the existing `GET /webapi/chat/history`, because it's shared.
- New controller `SpringAiChatController`, which copies `ChatController`'s flow (builder's
  call: leaves the current controller untouched). Its log line adds `impl=springai`, and like
  today it never logs the question, answer, email or token.

### 5.5 UI (PGCUI)

- Next to the current chat button, a second button for logged-in members only. It shows a
  circled letter **S** and has `aria-label="Open club assistant (Spring AI)"`.
- It opens the same side panel component, titled "Club assistant (Spring AI)" and posting to
  `/webapi/chat/spring`. Opening one bot closes the other.
- The panel loads the shared history on open, exactly as today.

### 5.6 Configuration and secrets

No new secrets and no new property file. Production keeps the key in
`/etc/pgc/chatbot.properties`.

## 6. Testing (what must be proven)

- Tool loop:
  - the Spring AI assistant calls a tool through the fake MCP server, with the member's token
    in the `Authorization` header;
  - it stops at 5 tool calls with `TOO_MANY_LOOKUPS`;
  - it stops at the deadline;
  - it maps 401 and other failures to the exceptions above.
- History pairs and the system prompt reach the model request, using a loopback fake of the
  Anthropic API, as the current SDK test does.
- Usage is priced and recorded once per model call.
- Controller:
  - `/webapi/chat/spring` needs a login and rejects non-JSON;
  - it shares the daily count and budget with `/webapi/chat`;
  - it refunds on failure.
- The app context still starts with no API key.
- UI: the S button shows only when logged in; the two panels open one at a time; the S panel
  posts to `/webapi/chat/spring`.
- The full existing suite stays green.

## 7. Rollout

Build and test locally (log in, then ask the S bot a question). Then deploy with the usual
procedure. No database or Cognito changes.

## 8. Risks the builder must check (builder's calls on how)

- **Thinking blocks in the tool loop:** if the model returns thinking blocks, they must go back
  to Claude unchanged on the next round. Prove with a real local question that uses a tool. If
  Spring AI 1.1.8 drops them and the API rejects the request, rule on a fix and record it.
- **Stop reasons:** Spring AI reports `stop_reason` as the generation's finish reason. Map
  "refusal" → `CANNOT_HELP` and "max_tokens" with tool calls → `TOO_MANY_LOOKUPS`, matching
  the current bot.
- **WAR size:** `spring-ai-anthropic` brings `spring-webflux` and Reactor. The app must stay a
  servlet app, and the WAR will grow.

## 9. Left to the builder

Class names inside `com.shiyuan.chat.springai`, the exact mapping of MCP SDK exceptions,
and the UI's styling of the circled S.

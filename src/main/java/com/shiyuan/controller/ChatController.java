package com.shiyuan.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.shiyuan.chat.ChatAssistant;
import com.shiyuan.chat.ChatBudget;
import com.shiyuan.chat.ChatExchangeDto;
import com.shiyuan.chat.ChatHistoryService;
import com.shiyuan.chat.ChatLimits;
import com.shiyuan.chat.ChatUsage;
import com.shiyuan.chat.McpGateway;
import com.shiyuan.chat.McpSession;
import com.shiyuan.chat.McpUnauthorizedException;
import com.shiyuan.chat.McpUnavailableException;
import com.shiyuan.chat.MemberTokenProvider;
import com.shiyuan.chat.ModelUnavailableException;
import com.shiyuan.chat.PlayerRef;
import com.shiyuan.chat.PlayerResolver;
import com.shiyuan.chat.SignInRequiredException;
import com.shiyuan.dto.ChatRequest;
import com.shiyuan.dto.ChatResponse;

/**
 * The club assistant. Signed-in members only ({@code /webapi/chat/**} is authenticated in
 * SecurityConfiguration).
 *
 * <p>The POST accepts JSON only. CSRF protection is off app-wide, so this is what stops another
 * site from spending the budget through a member's cookie: a cross-site JSON request needs a
 * CORS preflight, which only the club's own origins pass.
 *
 * <p>Logs one line per question -- member sub, outcome, tokens, cost. Never the question, the
 * answer, an email or a token.
 */
@RestController
public class ChatController {

    static final int MAX_QUESTION = 500;
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatBudget budget;
    private final ChatLimits limits;
    private final PlayerResolver players;
    private final MemberTokenProvider tokens;
    private final McpGateway mcp;
    private final ChatAssistant assistant;
    private final ChatHistoryService history;
    private final Clock clock;

    public ChatController(ChatBudget budget, ChatLimits limits, PlayerResolver players, MemberTokenProvider tokens,
                          McpGateway mcp, ChatAssistant assistant, ChatHistoryService history, Clock chatClock) {
        this.budget = budget;
        this.limits = limits;
        this.players = players;
        this.tokens = tokens;
        this.mcp = mcp;
        this.assistant = assistant;
        this.history = history;
        this.clock = chatClock;
    }

    @GetMapping(path = "/webapi/chat/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ChatExchangeDto> history(@AuthenticationPrincipal OidcUser member) {
        return history.recent(member.getSubject());
    }

    @PostMapping(path = "/webapi/chat", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> ask(@RequestBody ChatRequest body, Authentication auth,
                                            @AuthenticationPrincipal OidcUser member,
                                            HttpServletRequest request, HttpServletResponse response) {
        String question = body == null || body.question() == null ? "" : body.question().trim();
        if (question.isEmpty() || question.length() > MAX_QUESTION) {
            return ResponseEntity.badRequest().body(ChatResponse.refusal("invalid",
                    "Please ask a question of at most " + MAX_QUESTION + " characters."));
        }
        String sub = member.getSubject();

        if (budget.exhausted()) {
            log.info("chat sub={} outcome=budget", sub);
            return ResponseEntity.ok(ChatResponse.refusal("budget",
                    "The club assistant is resting until next month."));
        }
        if (!limits.tryConsume(sub)) {
            log.info("chat sub={} outcome=daily_limit", sub);
            return ResponseEntity.ok(ChatResponse.refusal("daily_limit",
                    "You've reached today's limit of questions. Please try again tomorrow."));
        }

        ChatUsage usage = new ChatUsage();
        String outcome = "error";
        try {
            Optional<PlayerRef> me = players.resolve(member);
            String accessToken = tokens.accessToken(auth, request, response);
            String answer;
            try (McpSession session = mcp.open(accessToken)) {
                answer = assistant.answer(question, me, history.recent(sub), session, usage, LocalDate.now(clock));
            }
            history.save(sub, question, answer);
            outcome = "answered";
            return ResponseEntity.ok(ChatResponse.answer(answer));
        } catch (SignInRequiredException e) {
            // Messages of these exceptions are token-free by construction; the cause tells a
            // lost session ("no authorized client") from an expired refresh ("invalid_grant").
            outcome = "signin:" + e.getMessage();
            return signInAgain(sub);
        } catch (McpUnauthorizedException e) {
            // The MCP server rejected the member's token -- e.g. a refreshed token without aud.
            outcome = "signin:mcp " + e.getMessage();
            return signInAgain(sub);
        } catch (ModelUnavailableException | McpUnavailableException e) {
            outcome = "unavailable:" + e.getMessage();
            limits.refund(sub);
            return ResponseEntity.ok(ChatResponse.refusal("unavailable",
                    "The club assistant can't answer right now. Please try again in a few minutes."));
        } finally {
            if (usage.inputTokens() > 0 || usage.outputTokens() > 0) {
                budget.record(usage.cost(), usage.inputTokens(), usage.outputTokens());
            }
            log.info("chat sub={} outcome={} in={} out={} cost={}", sub, outcome,
                    usage.inputTokens(), usage.outputTokens(), usage.cost().toPlainString());
        }
    }

    private ResponseEntity<ChatResponse> signInAgain(String sub) {
        limits.refund(sub);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ChatResponse.refusal("signin", "Please sign in again to use the club assistant."));
    }
}

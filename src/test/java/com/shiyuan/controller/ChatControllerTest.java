package com.shiyuan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.shiyuan.chat.ChatAssistant;
import com.shiyuan.chat.ChatBudget;
import com.shiyuan.chat.ChatExchangeDto;
import com.shiyuan.chat.ChatHistoryService;
import com.shiyuan.chat.ChatLimits;
import com.shiyuan.chat.ChatUsage;
import com.shiyuan.chat.McpGateway;
import com.shiyuan.chat.McpSession;
import com.shiyuan.chat.McpUnauthorizedException;
import com.shiyuan.chat.MemberTokenProvider;
import com.shiyuan.chat.ModelUnavailableException;
import com.shiyuan.chat.PlayerRef;
import com.shiyuan.chat.PlayerResolver;
import com.shiyuan.chat.SignInRequiredException;

/**
 * The chat endpoints through the real security filter chain, with every chat collaborator
 * mocked: who may call, in what order the checks run, and what is recorded on each outcome.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@ExtendWith(OutputCaptureExtension.class)
class ChatControllerTest {

    @Autowired MockMvc mvc;

    @MockBean ChatBudget budget;
    @MockBean ChatLimits limits;
    @MockBean PlayerResolver players;
    @MockBean MemberTokenProvider tokens;
    @MockBean McpGateway mcp;
    @MockBean ChatAssistant assistant;
    @MockBean ChatHistoryService history;
    @MockBean McpSession session;

    private static OidcLoginRequestPostProcessor member(String sub) {
        return oidcLogin().idToken(t -> t.subject(sub).claim("email", "member@example.test"));
    }

    private static String ask(String q) {
        return "{\"question\":\"" + q + "\"}";
    }

    @BeforeEach
    void happyDefaults() {
        when(budget.exhausted()).thenReturn(false);
        when(limits.tryConsume(anyString())).thenReturn(true);
        when(players.resolve(any())).thenReturn(Optional.of(new PlayerRef(42, "Jane Lee")));
        when(tokens.accessToken(any(), any(), any())).thenReturn("member-token");
        when(mcp.open("member-token")).thenReturn(session);
    }

    /** Make the assistant answer, spending the given tokens and cost on the meter it is handed. */
    private void assistantAnswers(String text, long in, long out, String cost) {
        when(assistant.answer(anyString(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            meter(inv.getArgument(4), in, out, cost);
            return text;
        });
    }

    private static void meter(ChatUsage usage, long in, long out, String cost) {
        try {
            var add = ChatUsage.class.getDeclaredMethod("add", long.class, long.class, BigDecimal.class);
            add.setAccessible(true);
            add.invoke(usage, in, out, new BigDecimal(cost));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void anonymous_post_401() throws Exception {
        mvc.perform(post("/webapi/chat").contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isUnauthorized());
        verify(assistant, never()).answer(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void anonymous_history_401() throws Exception {
        mvc.perform(get("/webapi/chat/history")).andExpect(status().isUnauthorized());
    }

    /** Only JSON: a plain cross-site form post cannot reach this endpoint without a CORS preflight. */
    @Test
    void textPlain_415() throws Exception {
        mvc.perform(post("/webapi/chat").with(member("sub-A")).contentType(MediaType.TEXT_PLAIN).content("hi"))
                .andExpect(status().isUnsupportedMediaType());
        verify(limits, never()).tryConsume(anyString());
    }

    @Test
    void formPost_415() throws Exception {
        mvc.perform(post("/webapi/chat").with(member("sub-A"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("question=hi"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void blank_400() throws Exception {
        mvc.perform(post("/webapi/chat").with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("   ")))
                .andExpect(status().isBadRequest());
        verify(limits, never()).tryConsume(anyString());
    }

    @Test
    void tooLong_400() throws Exception {
        mvc.perform(post("/webapi/chat").with(member("sub-A")).contentType(MediaType.APPLICATION_JSON)
                        .content(ask("x".repeat(501))))
                .andExpect(status().isBadRequest());
        verify(limits, never()).tryConsume(anyString());
    }

    @Test
    void budgetExhausted_noModelCall() throws Exception {
        when(budget.exhausted()).thenReturn(true);
        mvc.perform(post("/webapi/chat").with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refusal").value("budget"))
                .andExpect(jsonPath("$.answer").doesNotExist());
        verify(assistant, never()).answer(anyString(), any(), any(), any(), any(), any(), any());
        verify(limits, never()).tryConsume(anyString());
    }

    @Test
    void dailyLimit_refusal() throws Exception {
        when(limits.tryConsume("sub-A")).thenReturn(false);
        mvc.perform(post("/webapi/chat").with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refusal").value("daily_limit"));
        verify(assistant, never()).answer(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void signInRequired_401_andRefund() throws Exception {
        when(tokens.accessToken(any(), any(), any())).thenThrow(new SignInRequiredException("no client"));
        mvc.perform(post("/webapi/chat").with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.refusal").value("signin"));
        verify(limits).refund("sub-A");
        verify(assistant, never()).answer(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void mcp401_signin_refund_costRecorded() throws Exception {
        when(assistant.answer(anyString(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            meter(inv.getArgument(4), 100, 10, "0.0003");
            throw new McpUnauthorizedException("tools/call: HTTP 401");
        });
        mvc.perform(post("/webapi/chat").with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.refusal").value("signin"));
        verify(limits).refund("sub-A");
        verify(budget).record(new BigDecimal("0.0003"), 100L, 10L);
        verify(history, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void modelDown_unavailable_costRecorded_noHistory_refund() throws Exception {
        when(assistant.answer(anyString(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            meter(inv.getArgument(4), 500, 50, "0.0015");
            throw new ModelUnavailableException("Claude API: InternalServerException");
        });
        mvc.perform(post("/webapi/chat").with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refusal").value("unavailable"));
        verify(budget).record(new BigDecimal("0.0015"), 500L, 50L);
        verify(limits).refund("sub-A");
        verify(history, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void success_recordsCost_savesHistory_usesThisMembersToken() throws Exception {
        assistantAnswers("You're #7.", 1200, 100, "0.0034");
        mvc.perform(post("/webapi/chat").with(member("sub-A")).contentType(MediaType.APPLICATION_JSON)
                        .content(ask("Where am I?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("You're #7."))
                .andExpect(jsonPath("$.refusal").doesNotExist());
        verify(mcp).open("member-token");
        verify(budget).record(new BigDecimal("0.0034"), 1200L, 100L);
        verify(history).save("sub-A", "Where am I?", "You're #7.");
        verify(limits, never()).refund(anyString());
    }

    @Test
    void history_returnsOnlyThisMembersSub() throws Exception {
        when(history.recent("sub-A")).thenReturn(List.of(
                new ChatExchangeDto("q1", "a1", Instant.parse("2026-11-15T10:00:00Z"))));
        mvc.perform(get("/webapi/chat/history").with(member("sub-A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].question").value("q1"))
                .andExpect(jsonPath("$[0].answer").value("a1"));
        verify(history).recent(eq("sub-A"));
    }

    @Test
    void sessionIsClosedAfterTheQuestion() throws Exception {
        assistantAnswers("ok", 1, 1, "0.000012");
        mvc.perform(post("/webapi/chat").with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isOk());
        verify(session).close();
    }

    /** "Sign in again" has three causes; the log must say which (all messages are token-free). */
    @Test
    void signin_logsItsCause_noSessionClient(CapturedOutput log) throws Exception {
        when(tokens.accessToken(any(), any(), any())).thenThrow(new SignInRequiredException("no authorized client in session"));
        mvc.perform(post("/webapi/chat").with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(log.getOut()).contains("outcome=signin:no authorized client in session");
    }

    @Test
    void signin_logsItsCause_mcpRejectedToken(CapturedOutput log) throws Exception {
        when(assistant.answer(anyString(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new McpUnauthorizedException("tools/call: HTTP 401"));
        mvc.perform(post("/webapi/chat").with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(log.getOut()).contains("outcome=signin:mcp tools/call: HTTP 401");
    }

    @Test
    void adminProfile_passesAdminRole() throws Exception {
        assistantAnswers("Done.", 10, 5, "0.0001");
        mvc.perform(post("/webapi/chat").with(oidcLogin().idToken(t -> t.subject("sub-A").claim("profile", "admin")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Create a tournament\"}"))
                .andExpect(status().isOk());
        verify(assistant).answer(anyString(), any(), any(), any(), any(), any(), eq(com.shiyuan.chat.ChatRole.ADMIN));
    }

    @Test
    void noProfile_passesMemberRole() throws Exception {
        assistantAnswers("Hi.", 10, 5, "0.0001");
        mvc.perform(post("/webapi/chat").with(member("sub-A"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Hi\"}"))
                .andExpect(status().isOk());
        verify(assistant).answer(anyString(), any(), any(), any(), any(), any(), eq(com.shiyuan.chat.ChatRole.MEMBER));
    }
}

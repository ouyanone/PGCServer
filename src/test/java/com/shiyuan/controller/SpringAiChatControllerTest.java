package com.shiyuan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.shiyuan.chat.ChatBudget;
import com.shiyuan.chat.ChatHistoryService;
import com.shiyuan.chat.ChatLimits;
import com.shiyuan.chat.ChatUsage;
import com.shiyuan.chat.McpUnauthorizedException;
import com.shiyuan.chat.MemberTokenProvider;
import com.shiyuan.chat.ModelUnavailableException;
import com.shiyuan.chat.PlayerRef;
import com.shiyuan.chat.PlayerResolver;
import com.shiyuan.chat.SignInRequiredException;
import com.shiyuan.chat.springai.SpringAiChatAssistant;

/**
 * {@code POST /webapi/chat/spring} through the real security filter chain, collaborators mocked.
 * Same checks, order and records as the SDK bot's endpoint; the shared beans (budget, daily
 * limit, history) are the very ones {@code ChatController} uses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@ExtendWith(OutputCaptureExtension.class)
class SpringAiChatControllerTest {

    private static final String URL = "/webapi/chat/spring";

    @Autowired MockMvc mvc;

    @MockBean ChatBudget budget;
    @MockBean ChatLimits limits;
    @MockBean PlayerResolver players;
    @MockBean MemberTokenProvider tokens;
    @MockBean SpringAiChatAssistant assistant;
    @MockBean ChatHistoryService history;

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
        when(history.recent("sub-A")).thenReturn(List.of());
    }

    private void answers(String text, long in, long out, String cost) {
        when(assistant.answer(anyString(), any(), any(), anyString(), any(), any(), any())).thenAnswer(inv -> {
            ((ChatUsage) inv.getArgument(4)).add(in, out, new BigDecimal(cost));
            return text;
        });
    }

    private void verifyNoAnswer() {
        verify(assistant, never()).answer(anyString(), any(), any(), anyString(), any(), any(), any());
    }

    @Test
    void anonymous_post_401() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isUnauthorized());
        verifyNoAnswer();
    }

    @Test
    void textPlain_415() throws Exception {
        mvc.perform(post(URL).with(member("sub-A")).contentType(MediaType.TEXT_PLAIN).content("hi"))
                .andExpect(status().isUnsupportedMediaType());
        verify(limits, never()).tryConsume(anyString());
    }

    @Test
    void blank_400() throws Exception {
        mvc.perform(post(URL).with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("   ")))
                .andExpect(status().isBadRequest());
        verify(limits, never()).tryConsume(anyString());
    }

    @Test
    void tooLong_400() throws Exception {
        mvc.perform(post(URL).with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("x".repeat(501))))
                .andExpect(status().isBadRequest());
        verify(limits, never()).tryConsume(anyString());
    }

    @Test
    void budgetExhausted_noModelCall() throws Exception {
        when(budget.exhausted()).thenReturn(true);
        mvc.perform(post(URL).with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refusal").value("budget"));
        verifyNoAnswer();
        verify(limits, never()).tryConsume(anyString());
    }

    @Test
    void dailyLimit_refusal() throws Exception {
        when(limits.tryConsume("sub-A")).thenReturn(false);
        mvc.perform(post(URL).with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refusal").value("daily_limit"));
        verifyNoAnswer();
    }

    @Test
    void answered_savesHistory_recordsSpend(CapturedOutput log) throws Exception {
        answers("You're #7.", 1200, 100, "0.0034");
        mvc.perform(post(URL).with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("Where am I?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("You're #7."));
        verify(limits).tryConsume("sub-A");
        verify(assistant).answer(eq("Where am I?"), eq(Optional.of(new PlayerRef(42, "Jane Lee"))), eq(List.of()),
                eq("member-token"), any(), any(), eq(com.shiyuan.chat.ChatRole.MEMBER));
        verify(history).save("sub-A", "Where am I?", "You're #7.");
        verify(budget).record(new BigDecimal("0.0034"), 1200L, 100L);
        verify(limits, never()).refund(anyString());
        assertThat(log.getOut()).contains("chat impl=springai sub=sub-A outcome=answered")
                .doesNotContain("member-token").doesNotContain("Where am I?").doesNotContain("You're #7.");
    }

    @Test
    void modelUnavailable_refunds() throws Exception {
        when(assistant.answer(anyString(), any(), any(), anyString(), any(), any(), any())).thenAnswer(inv -> {
            ((ChatUsage) inv.getArgument(4)).add(500, 50, new BigDecimal("0.0015"));
            throw new ModelUnavailableException("model call failed: TransientAiException");
        });
        mvc.perform(post(URL).with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refusal").value("unavailable"));
        verify(limits).refund("sub-A");
        verify(budget).record(new BigDecimal("0.0015"), 500L, 50L);
        verify(history, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void mcpUnauthorized_401_refunds(CapturedOutput log) throws Exception {
        when(assistant.answer(anyString(), any(), any(), anyString(), any(), any(), any()))
                .thenThrow(new McpUnauthorizedException("mcp tools/call: HTTP 401"));
        mvc.perform(post(URL).with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.refusal").value("signin"));
        verify(limits).refund("sub-A");
        assertThat(log.getOut()).contains("outcome=signin:mcp mcp tools/call: HTTP 401");
    }

    @Test
    void signInRequired_401_refunds() throws Exception {
        when(tokens.accessToken(any(), any(), any())).thenThrow(new SignInRequiredException("no authorized client in session"));
        mvc.perform(post(URL).with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("hi")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.refusal").value("signin"));
        verify(limits).refund("sub-A");
        verifyNoAnswer();
    }

    @Test
    void adminProfile_passesAdminRole() throws Exception {
        answers("Done.", 10, 5, "0.0001");
        mvc.perform(post(URL).with(oidcLogin().idToken(t -> t.subject("sub-A").claim("profile", "admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(ask("Create a tournament")))
                .andExpect(status().isOk());
        verify(assistant).answer(anyString(), any(), any(), anyString(), any(), any(), eq(com.shiyuan.chat.ChatRole.ADMIN));
    }

    @Test
    void noProfile_passesMemberRole() throws Exception {
        answers("Hi.", 10, 5, "0.0001");
        mvc.perform(post(URL).with(member("sub-A")).contentType(MediaType.APPLICATION_JSON).content(ask("Hi")))
                .andExpect(status().isOk());
        verify(assistant).answer(anyString(), any(), any(), anyString(), any(), any(), eq(com.shiyuan.chat.ChatRole.MEMBER));
    }
}

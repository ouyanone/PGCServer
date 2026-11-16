package com.shiyuan.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * The chatbot forwards the member's own access token to the MCP server. It must come from the
 * member's session through Spring's manager (which refreshes an expired token), and every way
 * of not getting one must become "sign in again" -- never a leaked token in an error message.
 */
class MemberTokenProviderTest {

    private static final String TOKEN = "eyJ.member-access-token.sig";

    private final OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
    private final MemberTokenProvider provider = new MemberTokenProvider(manager);
    private final Authentication member = new TestingAuthenticationToken("member-sub", "n/a");

    private static OAuth2AuthorizedClient clientWith(String token) {
        ClientRegistration reg = ClientRegistration.withRegistrationId("cognito")
                .clientId("c").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/x").authorizationUri("https://a.test/authorize")
                .tokenUri("https://a.test/token").build();
        OAuth2AccessToken at = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, token,
                Instant.now(), Instant.now().plusSeconds(3600));
        return new OAuth2AuthorizedClient(reg, "member-sub", at);
    }

    private String call() {
        return provider.accessToken(member, new MockHttpServletRequest(), new MockHttpServletResponse());
    }

    @Test
    void returnsTokenValueFromAuthorizedClient() {
        when(manager.authorize(any())).thenReturn(clientWith(TOKEN));
        assertEquals(TOKEN, call());
    }

    @Test
    void asksForTheCognitoRegistrationAsThisMember() {
        ArgumentCaptor<OAuth2AuthorizeRequest> req = ArgumentCaptor.forClass(OAuth2AuthorizeRequest.class);
        when(manager.authorize(req.capture())).thenReturn(clientWith(TOKEN));
        call();
        assertEquals("cognito", req.getValue().getClientRegistrationId());
        assertEquals(member, req.getValue().getPrincipal());
    }

    @Test
    void nullAuthorizedClient_throwsSignInRequired() {
        when(manager.authorize(any())).thenReturn(null);
        assertThrows(SignInRequiredException.class, this::call);
    }

    @Test
    void refreshFailure_throwsSignInRequired_withoutTheToken() {
        when(manager.authorize(any())).thenThrow(new ClientAuthorizationException(
                new OAuth2Error("invalid_grant", "refresh token " + TOKEN + " expired", null), "cognito"));
        SignInRequiredException e = assertThrows(SignInRequiredException.class, this::call);
        assertFalse(String.valueOf(e.getMessage()).contains(TOKEN));
        assertFalse(String.valueOf(e.getCause()).contains(TOKEN), "cause must not carry the token text");
    }
}

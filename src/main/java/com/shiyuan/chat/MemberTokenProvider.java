package com.shiyuan.chat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;

/**
 * The signed-in member's Cognito access token, for forwarding to the MCP server.
 *
 * <p>The token lives in the HTTP session, put there by {@code oauth2Login}; the browser never
 * sees it. Going through {@link OAuth2AuthorizedClientManager} rather than reading the session
 * directly is what renews an expired access token with the refresh token.
 */
@Component
public class MemberTokenProvider {

    static final String REGISTRATION_ID = "cognito";

    private final OAuth2AuthorizedClientManager manager;

    public MemberTokenProvider(OAuth2AuthorizedClientManager manager) {
        this.manager = manager;
    }

    public String accessToken(Authentication member, HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizeRequest authorize = OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
                .principal(member)
                .attribute(HttpServletRequest.class.getName(), request)
                .attribute(HttpServletResponse.class.getName(), response)
                .build();
        OAuth2AuthorizedClient client;
        try {
            client = manager.authorize(authorize);
        } catch (OAuth2AuthorizationException e) {
            // Only the error code: the description may quote token material.
            throw new SignInRequiredException("token refresh failed: " + e.getError().getErrorCode());
        }
        if (client == null || client.getAccessToken() == null) {
            throw new SignInRequiredException("no authorized client in session");
        }
        return client.getAccessToken().getTokenValue();
    }
}

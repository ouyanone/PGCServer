package com.shiyuan.config;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Adds {@code resource=<MCP server>} to the Cognito authorize request.
 *
 * <p>Cognito stamps that value into the access token's {@code aud} only when the authorize
 * request names it (RFC 8707). The chatbot forwards the member's access token to the MCP
 * server, which refuses any token without that audience. Everything else about the request is
 * Spring's default.
 */
public class ResourceParameterResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final String resource;

    public ResourceParameterResolver(ClientRegistrationRepository registrations, String resource) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        this.resource = resource;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return withResource(delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return withResource(delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest withResource(OAuth2AuthorizationRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, Object> params = new HashMap<>(request.getAdditionalParameters());
        params.put("resource", resource);
        return OAuth2AuthorizationRequest.from(request).additionalParameters(params).build();
    }
}

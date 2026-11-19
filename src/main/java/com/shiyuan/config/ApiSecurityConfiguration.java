package com.shiyuan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The token-only door: {@code /api/**}, for callers that present a Cognito access token as
 * {@code Authorization: Bearer} -- the MCP server acting for a signed-in member. No session, no
 * cookie, no website login: those belong to {@code /webapi/**} ({@link SecurityConfiguration}),
 * which this chain never touches.
 *
 * <p>{@code /api/admin/**} needs the write scope. Any valid write-scoped token counts as an admin
 * (design D2, a deliberate simplification): Cognito access tokens do not carry the {@code profile}
 * attribute the website's admin check reads.
 */
@Configuration
public class ApiSecurityConfiguration {

    /**
     * Cognito marks every token with what it is for. An ID token describes a user to the app that
     * signed them in; it is not permission to call an API. A token with no {@code token_use} at
     * all is refused too.
     */
    public static final OAuth2TokenValidator<Jwt> ACCESS_TOKEN_ONLY = token -> {
        String tokenUse = token.getClaimAsString("token_use");
        if ("access".equals(tokenUse)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                "token_use is " + (tokenUse == null ? "absent" : "\"" + tokenUse + "\"")
                        + "; /api accepts only an access token", null));
    };

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtDecoder apiJwtDecoder,
            @Value("${pgc.api.write-scope:https://pgcgolf.club/mcp/tools.write}") String writeScope)
            throws Exception {
        return http
                .securityMatcher("/api/**")
                // Bearer token on every request; nothing for a cross-site request to ride on.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/api/admin/**").hasAuthority("SCOPE_" + writeScope)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.decoder(apiJwtDecoder)))
                .build();
    }

    /**
     * Validates against the same Cognito pool the website signs members in with. The key set is
     * fetched on first use, so startup (and the tests) need no network.
     */
    @Bean
    public JwtDecoder apiJwtDecoder(
            @Value("${spring.security.oauth2.client.provider.cognito.issuerUri}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), ACCESS_TOKEN_ONLY));
        return decoder;
    }
}

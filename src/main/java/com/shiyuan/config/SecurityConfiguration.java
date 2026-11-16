package com.shiyuan.config;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfiguration {

	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return web -> web.ignoring().requestMatchers(
			new AntPathRequestMatcher("/*.js"),
			new AntPathRequestMatcher("/*.css"),
			new AntPathRequestMatcher("/*.ico"),
			new AntPathRequestMatcher("/*.txt"),
			new AntPathRequestMatcher("/*.html"),
			new AntPathRequestMatcher("/*.jpg"),
			new AntPathRequestMatcher("/*.png"),
			new AntPathRequestMatcher("/*.gif"),
			new AntPathRequestMatcher("/*.woff"),
			new AntPathRequestMatcher("/*.woff2"),
			new AntPathRequestMatcher("/assets/**")
		);
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http,
			ClientRegistrationRepository registrations,
			@Value("${pgc.chat.mcp-resource:https://pgcgolf.club/mcp}") String mcpResource) throws Exception {
		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(authz -> authz
				.requestMatchers("/webapi/admin/**").authenticated()
				// The club assistant: signed-in members only.
				.requestMatchers("/webapi/chat/**").authenticated()
				// Submitting scores writes player_score rows and updates every player's
				// running PGC handicap, so it requires a signed-in user. Reading scores
				// (GET /webapi/event/score) stays public.
				.requestMatchers(HttpMethod.POST, "/webapi/event/scores").authenticated()
				.anyRequest().permitAll())
			.exceptionHandling(ex -> ex
				.defaultAuthenticationEntryPointFor(
					(request, response, authException) ->
						response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"),
					new AntPathRequestMatcher("/webapi/**")))
			// The login also asks for a token addressed to the MCP server (resource= / aud), so the
			// club assistant can call the MCP tools as the signed-in member.
			.oauth2Login(login -> login.authorizationEndpoint(endpoint -> endpoint
				.authorizationRequestResolver(new ResourceParameterResolver(registrations, mcpResource))))
			.logout(logout -> logout.logoutSuccessUrl("/"));
		return http.build();
	}
	
	
    /**
     * Hands out the signed-in member's Cognito tokens from the session, renewing an expired
     * access token with the refresh token. The club assistant forwards that access token to the
     * MCP server.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientRepository authorizedClients) {
        DefaultOAuth2AuthorizedClientManager manager =
                new DefaultOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build());
        return manager;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200", "https://www.pgcgolf.club", "https://pgcgolf.club"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}

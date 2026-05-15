package com.shiyuan.config;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(authz -> authz
				.requestMatchers("/webapi/admin/**").authenticated()
				.anyRequest().permitAll())
			.exceptionHandling(ex -> ex
				.defaultAuthenticationEntryPointFor(
					(request, response, authException) ->
						response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"),
					new AntPathRequestMatcher("/webapi/**")))
			.oauth2Login(Customizer.withDefaults())
			.logout(logout -> logout.logoutSuccessUrl("/"));
		return http.build();
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

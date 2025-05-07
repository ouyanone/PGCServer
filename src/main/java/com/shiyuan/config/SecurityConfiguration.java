package com.shiyuan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(Customizer.withDefaults())
				.authorizeHttpRequests(
						authz -> authz.requestMatchers("/webapi/admin/**").authenticated()
									  .anyRequest().permitAll())
				
						//authz -> authz
						//  .anyRequest().permitAll())
				.oauth2Login(Customizer.withDefaults())
				.logout(httpSecurityLogoutConfigurer -> httpSecurityLogoutConfigurer.logoutSuccessUrl("/"))
				.csrf(AbstractHttpConfigurer::disable);
		return http.build();
	}

}

package com.shiyuan.chat;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.test.context.ActiveProfiles;

/**
 * The member's Cognito tokens live in their HTTP session (spec §5.2), not in Spring Boot's
 * default in-memory map: they then end with the session, and survive a Tomcat restart exactly
 * as long as the session does -- so a still-signed-in member is not told to sign in again
 * after every deploy.
 */
@SpringBootTest
@ActiveProfiles("local")
class AuthorizedClientStorageTest {

    @Autowired OAuth2AuthorizedClientRepository authorizedClients;

    @Test
    void tokensAreKeptInTheHttpSession() {
        assertInstanceOf(HttpSessionOAuth2AuthorizedClientRepository.class, authorizedClients);
    }
}

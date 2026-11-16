package com.shiyuan.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * The website's login must ask Cognito for a token addressed to the MCP server. Cognito only
 * puts {@code aud} into the access token when the authorize request carries {@code resource=}
 * (RFC 8707), and the MCP server rejects any token without it.
 */
class ResourceParameterResolverTest {

    private static final String MCP = "https://pgcgolf.club/mcp";

    private final ResourceParameterResolver resolver = new ResourceParameterResolver(
            new InMemoryClientRegistrationRepository(cognito()), MCP);

    private static ClientRegistration cognito() {
        return ClientRegistration.withRegistrationId("cognito")
                .clientId("client")
                .clientSecret("not-a-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", MCP + "/tools.read")
                .authorizationUri("https://example.test/oauth2/authorize")
                .tokenUri("https://example.test/oauth2/token")
                .build();
    }

    private static MockHttpServletRequest get(String path) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        req.setServletPath(path);
        return req;
    }

    @Test
    void resolve_addsResourceParameter() {
        OAuth2AuthorizationRequest r = resolver.resolve(get("/oauth2/authorization/cognito"));
        assertEquals(MCP, r.getAdditionalParameters().get("resource"));
    }

    @Test
    void resolve_withExplicitRegistrationId_addsResourceParameter() {
        OAuth2AuthorizationRequest r = resolver.resolve(get("/oauth2/authorization/cognito"), "cognito");
        assertEquals(MCP, r.getAdditionalParameters().get("resource"));
    }

    @Test
    void resolve_returnsNullForOtherPaths() {
        assertNull(resolver.resolve(get("/webapi/news")));
    }

    /** Both profiles must request the MCP scope, or Cognito issues a token the server refuses. */
    @ParameterizedTest
    @ValueSource(strings = {"/application.properties", "/application-local.properties"})
    void loginScope_includesMcpToolsRead(String file) throws Exception {
        Properties p = new Properties();
        try (InputStream in = getClass().getResourceAsStream(file)) {
            p.load(in);
        }
        String scope = p.getProperty("spring.security.oauth2.client.registration.cognito.scope");
        assertTrue(Arrays.asList(scope.split("\\s*,\\s*")).contains(MCP + "/tools.read"),
                file + " scope was: " + scope);
    }
}

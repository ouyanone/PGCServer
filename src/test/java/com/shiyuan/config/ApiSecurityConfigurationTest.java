package com.shiyuan.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code /api/**} is a second, token-only door: a bearer access token with the write scope, and
 * nothing else -- not the website's session login. The website's {@code /webapi/**} does not
 * learn to accept bearer tokens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ApiSecurityConfigurationTest {

    private static final String READ = "SCOPE_https://pgcgolf.club/mcp/tools.read";
    private static final String WRITE = "SCOPE_https://pgcgolf.club/mcp/tools.write";

    @Autowired MockMvc mvc;

    @Test
    void apiAdmin_noToken_401() throws Exception {
        mvc.perform(get("/api/admin/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
    }

    @Test
    void apiAdmin_readScopeOnly_403() throws Exception {
        mvc.perform(get("/api/admin/anything").with(jwt().authorities(new SimpleGrantedAuthority(READ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiAdmin_writeScope_passesSecurity() throws Exception {
        // POST: GETs of unknown paths are forwarded to the Angular app (SpaForwardController).
        // Past security there is no handler for this path (404/405); what matters is "not 401/403".
        int status = mvc.perform(post("/api/admin/anything").with(jwt().authorities(new SimpleGrantedAuthority(WRITE))))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403).isGreaterThanOrEqualTo(404);
    }

    @Test
    void apiAdmin_sessionLogin_isNotEnough() throws Exception {
        // A website login lives in the HTTP session. Even one claiming the write scope must not
        // open /api: that chain never reads the session.
        OidcUser admin = new DefaultOidcUser(List.of(new SimpleGrantedAuthority(WRITE)),
                OidcIdToken.withTokenValue("id").subject("s").claim("profile", "admin")
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build());
        SecurityContext context = new SecurityContextImpl(
                new OAuth2AuthenticationToken(admin, admin.getAuthorities(), "cognito"));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        mvc.perform(post("/api/admin/anything").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webapiAdmin_bearerToken_notAuthenticated() throws Exception {
        MvcResult result = mvc.perform(post("/webapi/admin/tournaments")
                        .header("Authorization", "Bearer not-a-session")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void apiChain_createsNoSession() throws Exception {
        MvcResult result = mvc.perform(get("/api/admin/anything")
                        .with(jwt().authorities(new SimpleGrantedAuthority(WRITE))))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    }

    private static Jwt token(String tokenUse) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).subject("s");
        if (tokenUse != null) {
            b.claim("token_use", tokenUse);
        }
        return b.build();
    }

    @Test
    void accessTokenOnly_rejectsIdToken() {
        assertThat(ApiSecurityConfiguration.ACCESS_TOKEN_ONLY.validate(token("id")).hasErrors()).isTrue();
    }

    @Test
    void accessTokenOnly_rejectsMissingTokenUse() {
        assertThat(ApiSecurityConfiguration.ACCESS_TOKEN_ONLY.validate(token(null)).hasErrors()).isTrue();
    }

    @Test
    void accessTokenOnly_acceptsAccessToken() {
        assertThat(ApiSecurityConfiguration.ACCESS_TOKEN_ONLY.validate(token("access")).hasErrors()).isFalse();
    }
}

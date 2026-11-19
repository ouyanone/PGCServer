package com.shiyuan.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class ChatRoleTest {

    private static DefaultOidcUser user(Map<String, Object> claims) {
        OidcIdToken.Builder b = OidcIdToken.withTokenValue("id").subject("s")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(b::claim);
        return new DefaultOidcUser(List.of(), b.build());
    }

    @Test void adminProfile_isAdmin() { assertThat(ChatRole.of(user(Map.of("profile", "admin")))).isEqualTo(ChatRole.ADMIN); }
    @Test void adminProfileAnyCase_isAdmin() { assertThat(ChatRole.of(user(Map.of("profile", "Admin")))).isEqualTo(ChatRole.ADMIN); }
    @Test void noProfile_isMember() { assertThat(ChatRole.of(user(Map.of()))).isEqualTo(ChatRole.MEMBER); }
    @Test void otherProfile_isMember() { assertThat(ChatRole.of(user(Map.of("profile", "member")))).isEqualTo(ChatRole.MEMBER); }
    @Test void nullUser_isMember() { assertThat(ChatRole.of(null)).isEqualTo(ChatRole.MEMBER); }

    @Test void member_mayNotUseAdminTools() {
        assertThat(ChatRole.MEMBER.mayUse("admin_create_tournament")).isFalse();
        assertThat(ChatRole.MEMBER.mayUse("get_standings")).isTrue();
    }

    @Test void admin_mayUseAll() {
        assertThat(ChatRole.ADMIN.mayUse("admin_create_tournament")).isTrue();
        assertThat(ChatRole.ADMIN.mayUse("get_standings")).isTrue();
    }
}

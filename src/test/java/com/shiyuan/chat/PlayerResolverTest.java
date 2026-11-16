package com.shiyuan.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.repository.PlayerRepository;

/**
 * "Where am I in the standings?" needs the member's player record. The login email finds it
 * server-side; only the id and name ever leave this class.
 */
class PlayerResolverTest {

    private final PlayerRepository players = mock(PlayerRepository.class);
    private final PlayerResolver resolver = new PlayerResolver(players);

    private static OidcUser member(String email) {
        Map<String, Object> claims = email == null
                ? Map.of("sub", "sub-A")
                : Map.of("sub", "sub-A", "email", email);
        OidcIdToken id = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(60), claims);
        return new DefaultOidcUser(List.of(), id, "sub");
    }

    private static Player player(long id, String first, String last, String email) {
        Player p = new Player();
        p.setId(id);
        p.setfName(first);
        p.setlName(last);
        p.setEmail(email);
        return p;
    }

    @Test
    void oneMatch_returnsIdAndName() {
        when(players.findByEmailIgnoreCase("jane@example.com"))
                .thenReturn(List.of(player(42, "Jane", "Lee", "jane@example.com")));
        assertEquals(Optional.of(new PlayerRef(42, "Jane Lee")), resolver.resolve(member("jane@example.com")));
    }

    /** The login email is passed as given; matching case-insensitively is the query's job. */
    @Test
    void differentCase_stillMatches() {
        when(players.findByEmailIgnoreCase("Jane@Example.COM"))
                .thenReturn(List.of(player(42, "Jane", "Lee", "jane@example.com")));
        assertTrue(resolver.resolve(member("Jane@Example.COM")).isPresent());
    }

    @Test
    void noMatch_empty() {
        when(players.findByEmailIgnoreCase(anyString())).thenReturn(List.of());
        assertEquals(Optional.empty(), resolver.resolve(member("nobody@example.com")));
    }

    /** Two players sharing an email: guessing would answer as the wrong person. */
    @Test
    void twoMatches_empty() {
        when(players.findByEmailIgnoreCase("shared@example.com")).thenReturn(List.of(
                player(1, "A", "One", "shared@example.com"),
                player(2, "B", "Two", "shared@example.com")));
        assertEquals(Optional.empty(), resolver.resolve(member("shared@example.com")));
    }

    @Test
    void nullEmail_emptyWithoutQuery() {
        assertEquals(Optional.empty(), resolver.resolve(member(null)));
        verify(players, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void nullUser_empty() {
        assertEquals(Optional.empty(), resolver.resolve(null));
    }

    @Test
    void playerRef_toString_hasNoEmail() {
        when(players.findByEmailIgnoreCase("jane@example.com"))
                .thenReturn(List.of(player(42, "Jane", "Lee", "jane@example.com")));
        PlayerRef ref = resolver.resolve(member("jane@example.com")).orElseThrow();
        assertFalse(ref.toString().contains("@"), ref.toString());
    }
}

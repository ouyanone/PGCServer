package com.shiyuan.chat;

import java.util.List;
import java.util.Optional;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import com.shiyuan.dao.entity.db.Player;
import com.shiyuan.dao.repository.PlayerRepository;

/**
 * Finds the signed-in member's player record from their login email, so "my scores" works.
 *
 * <p>The email is used here and nowhere else: only the player's id and display name (built
 * the way the MCP tools build {@code playerName}) go on to the model. No match, or more than
 * one, means "unknown" -- the assistant then asks for a name rather than guessing.
 */
@Component
public class PlayerResolver {

    private final PlayerRepository players;

    public PlayerResolver(PlayerRepository players) {
        this.players = players;
    }

    public Optional<PlayerRef> resolve(OidcUser member) {
        String email = member == null ? null : member.getEmail();
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        List<Player> matches = players.findByEmailIgnoreCase(email.trim());
        if (matches.size() != 1) {
            return Optional.empty();
        }
        Player p = matches.get(0);
        String name = (text(p.getfName()) + " " + text(p.getlName())).trim();
        return Optional.of(new PlayerRef(p.getId(), name));
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }
}

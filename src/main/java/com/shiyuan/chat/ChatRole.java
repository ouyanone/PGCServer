package com.shiyuan.chat;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Who is asking, as far as the club assistant cares: a club admin may use the {@code admin_}
 * tools and gets more room per question; every other member gets the read-only tools.
 *
 * <p>The admin marker is the Cognito {@code profile} attribute, as the website's own admin check
 * ({@code BaseController.checkPrincipal}) reads it.
 */
public enum ChatRole {
    MEMBER,
    ADMIN;

    /** The prefix every MCP tool that changes club data carries. */
    public static final String ADMIN_TOOL_PREFIX = "admin_";

    public static ChatRole of(OidcUser member) {
        return member != null && "admin".equalsIgnoreCase(member.getProfile()) ? ADMIN : MEMBER;
    }

    public boolean mayUse(String toolName) {
        return this == ADMIN || toolName == null || !toolName.startsWith(ADMIN_TOOL_PREFIX);
    }
}

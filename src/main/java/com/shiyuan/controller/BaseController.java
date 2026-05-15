package com.shiyuan.controller;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public abstract class BaseController {

    protected void checkPrincipal(OidcUser principal) throws Exception {
        if (principal != null && !"admin".equalsIgnoreCase(principal.getProfile())) {
            throw new Exception("You donnot have privilidge to do this operation. Please ask Hexin to fix it.");
        }
    }
}

package com.shiyuan.controller;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    @GetMapping(path = "/webapi/auth/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getAuthStatus(@AuthenticationPrincipal OidcUser principal) {
        if (principal == null) {
            return ResponseEntity.ok(Map.of("loggedIn", false));
        }
        return ResponseEntity.ok(Map.of(
            "loggedIn", true,
            "name", principal.getFullName() != null ? principal.getFullName() : principal.getName()
        ));
    }
}

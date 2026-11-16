package com.shiyuan.chat;

/**
 * The member's session holds no usable Cognito token (never authorized, or the refresh token
 * expired or was revoked). The chat endpoint answers this with 401 and a "please sign in
 * again" refusal.
 *
 * <p>Deliberately carries no cause: OAuth error descriptions can quote token material, and
 * this exception's text may reach a log.
 */
public class SignInRequiredException extends RuntimeException {

    public SignInRequiredException(String message) {
        super(message, null, false, false);
    }
}

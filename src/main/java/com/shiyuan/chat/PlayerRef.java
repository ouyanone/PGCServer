package com.shiyuan.chat;

/** Who "me" is, as the model is told it: player id and display name. Never an email. */
public record PlayerRef(long id, String name) {}

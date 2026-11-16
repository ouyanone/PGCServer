package com.shiyuan.chat;

/** A tool's text result, and whether the server marked it as an error. */
public record ToolOutcome(String text, boolean isError) {}

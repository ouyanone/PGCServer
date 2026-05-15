package com.shiyuan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards unmatched GET requests (Angular client-side routes) to index.html.
 * The regex [^\\.] excludes paths with dots so static files (.js, .css, .png)
 * are still served normally. Spring resolves @RestController mappings first,
 * so /webapi/** routes are never caught here.
 */
@Controller
public class SpaForwardController {

    @GetMapping("/{path:[^\\.]*}")
    public String forwardSingle() {
        return "forward:/index.html";
    }

    @GetMapping("/{p1:[^\\.]*}/{p2:[^\\.]*}")
    public String forwardDouble() {
        return "forward:/index.html";
    }

    @GetMapping("/{p1:[^\\.]*}/{p2:[^\\.]*}/{p3:[^\\.]*}")
    public String forwardTriple() {
        return "forward:/index.html";
    }
}

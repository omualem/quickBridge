package com.example.quickbridge.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Serves the single-page frontend.
 *
 * Both the landing page ({@code /}) and a session page ({@code /{code}})
 * resolve to the same {@code index.html}; the frontend reads the path to decide
 * whether to auto-join a session. Forwarding (rather than redirecting) keeps the
 * {@code /{code}} URL in the address bar so it stays shareable.
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    /**
     * Matches a bare 5-character code path. The regex restricts this mapping to
     * the code alphabet so it never shadows static assets (e.g. app.js) or the
     * {@code /api} and {@code /ws} routes.
     */
    @GetMapping("/{code:[A-HJ-NP-Z2-9]{5}}")
    public String session(@PathVariable String code) {
        return "forward:/index.html";
    }
}

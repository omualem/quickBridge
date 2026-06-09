package com.example.quickbridge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC tweaks.
 *
 * CORS is opened up for the REST API so the page can be loaded from a phone
 * pointing at a computer's LAN address (common cross-device usage) during the
 * MVP. Tighten this before any production deployment.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS");
    }
}

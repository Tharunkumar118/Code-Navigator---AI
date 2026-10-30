package com.codenavigator.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration. Centralized here instead of per-controller
 * @CrossOrigin annotations, so one place controls which origins can call the API.
 *
 * Once Spring Security is added in Module 2, this same origin list will also
 * be wired into the SecurityFilterChain's CorsConfigurationSource.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:5173", "http://localhost:8000", "http://127.0.0.1:8000") // dev servers
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}



package com.example.carbon_credit.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(new String[]{"http://localhost:3000", "http://localhost:5173"}));  // FE origins (CRA 3000, Vite 5173)
        configuration.setAllowedMethods(List.of(new String[]{"GET", "POST", "PUT", "DELETE", "OPTIONS"}));  // All methods + OPTIONS preflight
        configuration.setAllowedHeaders(List.of(new String[]{"*"}));  // All headers (Authorization Bearer token)
        configuration.setAllowCredentials(true);  // Allow cookies/credentials (JWT)
        configuration.setMaxAge(3600L);  // Cache preflight 1h

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);  // Apply to all paths
        return source;
    }
}
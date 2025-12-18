package com.example.carbon_credit.Security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordlessEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private UserDetailsService userDetailsService;  // Cần implement UserDetailsService

//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordlessEncoder();  // Không dùng password vì auth bằng signature
//    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()  // Public login
                        .requestMatchers("/ws/**").permitAll()  // ← SỬA: Double ** để match SockJS subpaths
                        .requestMatchers("/api/auth/*").permitAll()  // Existing login
                        .requestMatchers("/api/orders/*").authenticated()  // Your orders endpoint
                        .requestMatchers("/api/projects/save", "/api/projects/allProject").permitAll()  // Public nếu cần
                        .requestMatchers("/api/projects/*/verify").authenticated()  // Bảo vệ verify
                        .requestMatchers("/api/projects/*/approved").authenticated()
                        .requestMatchers("/api/role-request/confirm").permitAll()
                        .requestMatchers("/api/role-request/request").authenticated()
                        .requestMatchers("/api/retire/request").authenticated()
                        .requestMatchers("/api/retire/approved-retire/*").authenticated()
                        .requestMatchers("/api/retire/confirm-onchain/*").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
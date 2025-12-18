package com.example.carbon_credit.Security;

import com.example.carbon_credit.Util.JwtUtil;  // Import JwtUtil
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private UserDetailsService userDetailsService;  // Để load UserDetails từ DB

    @Autowired
    private JwtUtil jwtUtil;  // ← THÊM: Autowire JwtUtil service

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;

        System.out.println("=== DEBUG JWT FILTER ===");  // Debug log
        System.out.println("Request URI: " + request.getRequestURI());  // /api/orders/place
        System.out.println("Auth header: " + authHeader);  // Bearer + token

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            System.out.println("Extracted token length: " + token.length());  // 228 OK
            System.out.println("Token preview: " + token.substring(0, 20) + "...");  // eyJhbGciOiJIUzM4NCJ9...

            try {
                username = jwtUtil.extractUsername(token);  // Bây giờ jwtUtil có sẵn
                System.out.println("Extracted username: " + username);  // 0x37b046a200F35b1c097bf9878d0162Fba6c61474
                System.out.println("Token exp: " + jwtUtil.getExpirationDateFromToken(token));  // Check exp > now

                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                System.out.println("UserDetails loaded: " + userDetails.getUsername() + ", authorities: " + userDetails.getAuthorities());  // ROLE_USER

                if (jwtUtil.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    System.out.println("JWT validated and set auth SUCCESS for: " + username);  // Success log
                } else {
                    System.out.println("JWT validation FAILED for: " + username + " (exp or signature invalid)");  // Fail log
                }
            } catch (Exception e) {
                System.out.println("JWT extract/validation EXCEPTION: " + e.getMessage());  // Exception log
                e.printStackTrace();
            }
        } else {
            System.out.println("No Bearer token in header");  // No token log
        }

        System.out.println("=== END DEBUG JWT FILTER ===");  // Log end

        filterChain.doFilter(request, response);
    }
}
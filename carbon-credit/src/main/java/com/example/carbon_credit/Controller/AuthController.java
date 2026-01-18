package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.LoginRequestDTO;
import com.example.carbon_credit.DTO.LoginResponse;
import com.example.carbon_credit.Service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = { "http://localhost:5173" }) // FE React/Vite port
@Slf4j
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Endpoint đăng nhập với Ethereum signature
     * POST /api/auth/login
     * Body: { "addressWallet": "0x...", "message": "Login...", "signature": "0x..."
     * }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO request) {
        log.info("🔐 Login attempt for address: {}", request.getAddress());
        log.debug("Message length: {}, Signature length: {}",
                request.getMessage().length(), request.getSignature().length());

        try {
            LoginResponse response = authService.login(request);
            log.info("✅ Login successful for user: {}", response.getUser().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Login failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Lỗi: " + e.getMessage());
        }
    }
}
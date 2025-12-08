package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.LoginRequestDTO;
import com.example.carbon_credit.DTO.LoginResponse;
import com.example.carbon_credit.Service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:5173"})  // FE React/Vite port
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Endpoint đăng nhập với Ethereum signature
     * POST /api/auth/login
     * Body: { "addressWallet": "0x...", "message": "Login...", "signature": "0x..." }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO request) {
        System.out.println("=== DEBUG LOGIN REQUEST ===");
        System.out.println("Address: " + request.getAddress());  // Hoặc getAddressWallet()
        System.out.println("Message length: " + request.getMessage().length());
        System.out.println("Signature length: " + request.getSignature().length());
        System.out.println("=== END DEBUG ===");

        try {
            LoginResponse response = authService.login(request);
            System.out.println("=== DEBUG LOGIN SUCCESS ===");
            System.out.println("User ID: " + response.getUser().getId());
            System.out.println("Token length: " + response.getToken().length());
            System.out.println("=== END DEBUG ===");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("=== DEBUG LOGIN ERROR ===");
            e.printStackTrace();  // Full stack
            System.err.println("Error message: " + e.getMessage());
            System.err.println("=== END DEBUG ===");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Lỗi: " + e.getMessage());
        }
    }
}
package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.LoginRequestDTO;
import com.example.carbon_credit.DTO.LoginResponse;
import com.example.carbon_credit.DTO.UserDto;
import com.example.carbon_credit.Entity.User;
import com.example.carbon_credit.Repository.UserRepository;
import com.example.carbon_credit.Service.SignatureVerifierService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

@Service
public class AuthService {

    @Value("${jwt.secret:your-super-secret-key-at-least-256-bits-long-for-hs256}")
    private String jwtSecret;  // Secret key cho JWT (ít nhất 256 bits, từ properties)

    private final UserRepository userRepository;
    private final SignatureVerifierService signatureVerifierService;

    @Autowired
    public AuthService(UserRepository userRepository, SignatureVerifierService signatureVerifierService) {
        this.userRepository = userRepository;
        this.signatureVerifierService = signatureVerifierService;
    }

    /**
     * Xử lý đăng nhập với Ethereum signature
     */
    public LoginResponse login(LoginRequestDTO request) {
        String address = request.getAddress().toLowerCase();  // Normalize lowercase
        String message = request.getMessage();
        String signature = request.getSignature();

        // Bước 1: Verify signature và recover address
        String recoveredAddress = signatureVerifierService.verifyAndRecoverAddress(message, signature);
        if (recoveredAddress == null || !address.equals(recoveredAddress.toLowerCase())) {
            throw new RuntimeException("Chữ ký không hợp lệ - địa chỉ không khớp!");
        }

        // Bước 2: Tìm hoặc tạo user theo address
        Optional<User> optionalUser = userRepository.findById(address);
        User user;
        if (optionalUser.isPresent()) {
            user = optionalUser.get();
//            // Update last_login nếu cần
//            user.setLastLogin(new Date());
        } else {
            // Tạo user mới với role mặc định "USER"
            user = new User();
            user.setId(address);  // Giả sử id là String, nếu Long thì parse hoặc dùng UUID
            user.setName("User " + address.substring(2, 10).toUpperCase());  // Default name từ address
            user.setEmail("");  // Optional, có thể yêu cầu update sau
            user.setRoleId("USER");  // Hoặc set role_id = 2 nếu dùng FK (String cho linh hoạt)
            user.setCreatedAt(LocalDateTime.now());  // Sửa typo: createAt → createdAt nếu entity có field này
            user = userRepository.save(user);
        }

        // Bước 3: Generate JWT token
        String token = generateJwtToken(user);

        // Bước 4: Trả response
        UserDto userDto = new UserDto(user.getId(), user.getName(), user.getRoleId());
        return new LoginResponse(token, userDto);
    }

    /**
     * Generate JWT token với claims cơ bản
     */
    private String generateJwtToken(User user) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(user.getId())
                .claim("role", user.getRoleId())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000L))  // Expire 24h
                .signWith(key)
                .compact();
    }

    // Trong AuthService.java
    public boolean hasRole(String name, String requiredRole) {  // name ở đây là user.id (address)
        return userRepository.findById(name)  // Dùng findById (từ JpaRepository<User, String>)
                .map(user -> user.getRoleId().equals(requiredRole))
                .orElse(false);
    }

}
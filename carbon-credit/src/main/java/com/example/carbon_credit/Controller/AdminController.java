package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.UserDto;  // Tạo DTO mới đơn giản (xem bên dưới)
import com.example.carbon_credit.DTO.UserDto;
import com.example.carbon_credit.Entity.User;
import com.example.carbon_credit.Service.UserService;
import com.example.carbon_credit.constants.UserRole;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("api/users")
public class AdminController {
    @Autowired
    private UserService userService;

    @PostMapping("/{id}/set-role")  // Sửa path rõ nghĩa, fix lỗi chính tả
    public ResponseEntity<?> setUserRole(
            @PathVariable String id,
            @Valid @RequestBody UserDto req,  // Simplify: chỉ cần roleId
            Principal principal
    ) {
        // Check quyền: giả sử principal có user, check role (bạn cần implement getCurrentUser từ principal)
        // Ví dụ: User currentUser = getCurrentUser(principal); if (!currentUser.getRoleId().equals(UserRole.GOVERNMENT)) throw new AccessDeniedException();

        User result = userService.setRole(id, req);  // Truyền roleId trực tiếp
        return ResponseEntity.ok(result);
    }
}
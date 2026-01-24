package com.example.carbon_credit.Controller;

import com.example.carbon_credit.Entity.Notification;
import com.example.carbon_credit.Entity.User;
import com.example.carbon_credit.Repository.NotificationRepository;
import com.example.carbon_credit.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    UserRepository userRepository;

    @GetMapping("/my-notifications")
    public List<Notification> getMyNotifications(Principal principal) {
        // Lấy địa chỉ ví từ Token (đã map vào username)
        String walletAddress = principal.getName();

        // Lấy Role từ Token (ví dụ: ROLE_USER, ROLE_ADMIN)
        User user = userRepository.findById(walletAddress)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String role = user.getRoleId();

        return notificationRepository.findMyNotifications(walletAddress, role);
    }
}

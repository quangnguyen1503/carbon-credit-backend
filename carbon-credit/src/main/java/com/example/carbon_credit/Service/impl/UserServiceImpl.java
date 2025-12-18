package com.example.carbon_credit.Service.impl;

import com.example.carbon_credit.DTO.UserDto;
import com.example.carbon_credit.Entity.User;
import com.example.carbon_credit.Repository.UserRepository;
import com.example.carbon_credit.Service.UserService;
import com.example.carbon_credit.constants.UserRole;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor  // Xóa @CrossOrigin, không cần ở Service
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private final UserRepository userRepository;

    @Override
    public User getUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
    }

    @Override
    public User setRole(String id, UserDto req) {
        // Validate role hợp lệ (dùng constants)
        String roleId = req.getRoleId();
        if (!isValidRole(roleId)) {
            throw new IllegalArgumentException("Invalid role: " + roleId + ". Must be one of: OWNER, VERIFIER, GOVERNMENT");
        }

        User user = getUserById(id);
        String oldRole = user.getRoleId();
        user.setRoleId(roleId);  // Dùng từ req, không hardcode

        // Không cần set updatedAt thủ công, @UpdateTimestamp sẽ handle
        User savedUser = userRepository.save(user);

        logger.info("Role updated for user {}: {} -> {}", id, oldRole, roleId);  // Audit log
        return savedUser;
    }

    private boolean isValidRole(String roleId) {
        return UserRole.OWNER.equals(roleId) ||
                UserRole.VERIFIER.equals(roleId) ||
                UserRole.GOVERNMENT.equals(roleId);
    }
}
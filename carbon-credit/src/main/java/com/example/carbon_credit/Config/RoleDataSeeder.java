package com.example.carbon_credit.Config;

import com.example.carbon_credit.Entity.Role;
import com.example.carbon_credit.Repository.RoleRepository;
import com.example.carbon_credit.constants.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoleDataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {

        // Danh sách các role cần khởi tạo từ file constants của bạn
        List<String> roles = Arrays.asList(
                UserRole.USER,
                UserRole.OWNER,
                UserRole.VERIFIER,
                UserRole.GOVERNMENT,
                UserRole.ADMIN
        );

        for (String roleName : roles) {
            createRoleIfNotFound(roleName);
        }
    }

    private void createRoleIfNotFound(String roleName) {
        if (!roleRepository.existsByName(roleName)) {
            Role role = Role.builder()
                    .name(roleName)
                    .build();
            roleRepository.save(role);
            log.info("Created default role: {}", roleName);
        } else {
            log.debug("Role {} already exists.", roleName);
        }
    }

}
package com.example.carbon_credit.Config;

import com.example.carbon_credit.Entity.VerifierRole;
import com.example.carbon_credit.Repository.VerifierRoleRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class VerifierDataSeeder implements CommandLineRunner {

    private final VerifierRoleRepository verifierRoleRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Checking and initializing default verifier organizations...");

        // Danh sách các tổ chức thẩm định mặc định (Mẫu thực tế)
        List<VerifierInitData> defaultVerifiers = Arrays.asList(
                new VerifierInitData("Verra", "Verified Carbon Standard (VCS) - The world's most widely used greenhouse gas crediting program."),
                new VerifierInitData("Gold Standard", "Gold Standard for the Global Goals - Ensure projects that reduce carbon emissions also feature the highest levels of environmental integrity."),
                new VerifierInitData("Global Carbon Council", "GCC - An international carbon credit and sustainable development program."),
                new VerifierInitData("American Carbon Registry", "ACR - A leading carbon offset program used in the voluntary and regulated carbon markets.")
        );

        for (VerifierInitData data : defaultVerifiers) {
            createVerifierIfNotFound(data.name, data.description);
        }
    }

    private void createVerifierIfNotFound(String name, String description) {
        if (!verifierRoleRepository.existsByOrganizationName(name)) {
            VerifierRole verifier = new VerifierRole();

            // Tự động sinh UUID cho ID vì Entity của bạn dùng String ID
            verifier.setId(UUID.randomUUID().toString());
            verifier.setOrganizationName(name);
            verifier.setDescription(description);
            // version sẽ được Hibernate tự động set là 0

            verifierRoleRepository.save(verifier);
            log.info("Created default verifier: {}", name);
        } else {
            log.debug("Verifier '{}' already exists.", name);
        }
    }

    // Class nội bộ (Helper) để giữ dữ liệu khởi tạo cho gọn code
    @AllArgsConstructor
    @Getter
    private static class VerifierInitData {
        String name;
        String description;
    }
}
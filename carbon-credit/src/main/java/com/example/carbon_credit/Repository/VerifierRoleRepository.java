package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.VerifierRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerifierRoleRepository extends JpaRepository<VerifierRole, String> {
    boolean existsByOrganizationName(String organizationName);
}

package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.RoleRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface RoleRequestRepository extends JpaRepository<RoleRequest, String> {
    Optional<RoleRequest> findByEmailToken(String emailToken);

    Optional<RoleRequest> findByUserIdAndStatus(String userId, String status);

    List<RoleRequest> findByStatus(String status);
}

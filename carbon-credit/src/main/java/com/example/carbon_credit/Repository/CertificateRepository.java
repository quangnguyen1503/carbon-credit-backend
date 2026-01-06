package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.Certificate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, String> {

    Optional<Certificate> findById(String id);

    Page<Certificate> findByStatus(String status, Pageable pageable);

    List<Certificate> findByCreatedAtBetween(
            LocalDateTime from,
            LocalDateTime to
    );
    List<Certificate> findByUserIdOrderByCreatedAtDesc(String userId);

}

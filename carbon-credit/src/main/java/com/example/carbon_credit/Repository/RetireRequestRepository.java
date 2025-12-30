package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.RetireRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RetireRequestRepository extends JpaRepository<RetireRequest, String> {

    Optional<RetireRequest> findById(String id);

    Page<RetireRequest> findByStatus(String status, Pageable pageable);

    List<RetireRequest> findByCreatedAtBetween(
            LocalDateTime from,
            LocalDateTime to
    );

}

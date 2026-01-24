package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.CarbonCredit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CarbonCreditRepository extends JpaRepository<CarbonCredit, String> {
    Optional<CarbonCredit> findByProjectId(String projectId);

    Optional<CarbonCredit> findByTokenId(long tokenId);
}

package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.CarbonCredit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarbonCreditRepository extends JpaRepository<CarbonCredit, String> {

}

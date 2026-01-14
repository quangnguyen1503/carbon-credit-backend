package com.example.carbon_credit.Repository;


import com.example.carbon_credit.Entity.CreditHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditHistoryRepository extends JpaRepository<CreditHistory, String> {

}

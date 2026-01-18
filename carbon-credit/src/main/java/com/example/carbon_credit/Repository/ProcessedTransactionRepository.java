package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.ProcessedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedTransactionRepository extends JpaRepository<ProcessedTransaction, String> {
    boolean existsByTxHash(String txHash);
}
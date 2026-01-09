package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.MintHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MintHistoryRepository
        extends JpaRepository<MintHistory, String> {

    List<MintHistory> findByProjectIdOrderByCreatedAtDesc(String projectId);

    List<MintHistory> findByCreditId(String creditId);
}


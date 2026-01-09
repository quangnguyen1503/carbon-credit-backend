package com.example.carbon_credit.Service;

import com.example.carbon_credit.Entity.CarbonCredit;
import com.example.carbon_credit.Entity.MintHistory;
import com.example.carbon_credit.Entity.Project;
import com.example.carbon_credit.Repository.CarbonCreditRepository;
import com.example.carbon_credit.Repository.MintHistoryRepository;
import com.example.carbon_credit.Repository.ProjectRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CarbonCreditService {
    @Autowired
    CarbonCreditRepository carbonCreditRepository;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    MintHistoryRepository mintHistoryRepository;


    @Transactional
    public CarbonCredit mintCarbonCredit(
            String projectId,
            String userId,
            String mintHash,
            long mintAmount
    ) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        CarbonCredit carbonCredit = carbonCreditRepository
                .findByProjectId(projectId)
                .orElseThrow(() -> new RuntimeException("CarbonCredit not found"));

        long newIssued = carbonCredit.getIssueAmount() + mintAmount;

        if (newIssued > carbonCredit.getTotalAmount()) {
            throw new IllegalStateException("Exceed total credits");
        }

        // 1️⃣ update state
        carbonCredit.setIssueAmount(newIssued);

        // 2️⃣ audit log
        MintHistory history = MintHistory.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .creditId(carbonCredit.getId())
                .userId(userId)
                .mintAmount(mintAmount)
                .txHash(mintHash)
                .createdAt(LocalDateTime.now())
                .build();

        mintHistoryRepository.save(history);

        return carbonCreditRepository.save(carbonCredit);
    }

}

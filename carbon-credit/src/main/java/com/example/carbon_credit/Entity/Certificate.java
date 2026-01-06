package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "certificate")
@Data
public class Certificate {

    @Id
    private String id;

    private String userId;
    private String reason;
    private Integer totalAmount;

    private String status; // PENDING_APPROVAL, APPROVED, ONCHAIN_CONFIRMED

    private String onchainTxHash;
    private String nftTokenId;

    private String approvedBy;

    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;

    @OneToMany(
            mappedBy = "certificate",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<CertificateRecord> records = new ArrayList<>();
}

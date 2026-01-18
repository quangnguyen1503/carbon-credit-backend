package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "certificate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Certificate {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "nft_token_id", unique = true, nullable = false)
    private BigInteger nftTokenId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "total_amount")
    private BigInteger totalAmount;

    @Column(name = "tx_hash")
    private String txHash;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "certificate", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CertificateRecord> records;
}
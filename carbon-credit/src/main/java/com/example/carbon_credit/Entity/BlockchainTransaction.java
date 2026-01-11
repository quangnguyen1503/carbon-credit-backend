package com.example.carbon_credit.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;

@Entity
@Table(name = "blockchain_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockchainTransaction {
    @Id
    @Column(name = "tx_hash")
    private String txHash; // Transaction Hash từ Blockchain làm Primary Key

    @Column(name = "wallet_address", nullable = false)
    private String walletAddress;

    @Column(name = "type")
    private String type; // DEPOSIT_NATIVE, DEPOSIT_CREDIT, WITHDRAW, SETTLEMENT

    @Column(name = "token_id")
    private BigInteger tokenId; // NULL nếu là Native ETH

    @Column(name = "amount", precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(name = "status")
    private String status; // PENDING, SUCCESS, FAILED

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

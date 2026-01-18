package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallets_credits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletCredit {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_id", nullable = false)
    private CarbonCredit carbonCredit;

    @Column(name = "available_balance", nullable = false)
    private BigInteger availableBalance; // Số dư Credit có thể đem bán

    @Column(name = "locked_balance", nullable = false)
    private BigInteger lockedBalance; // Số dư Credit đang treo ở các lệnh Sell chưa khớp

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Version
    private Long version;
}

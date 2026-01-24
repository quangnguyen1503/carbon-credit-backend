package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "credit_history")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditHistory {

    @Id
    @Column(name = "id", length = 255, nullable = false)
    private String id;

    @Column(name = "credit_id", length = 255)
    private String creditId;

    @Column(name = "from_wallet", length = 255)
    private String sellerId;

    @Column(name = "to_wallet", length = 255)
    private String buyerId;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "tx_hash", length = 255, nullable = true)
    private String txHash;

    @Column(name = "created_at")
    private LocalDateTime tradeAt;

}

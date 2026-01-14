package com.example.carbon_credit.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedTransaction {

    @Id
    @Column(name = "tx_hash", length = 66)
    private String txHash;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
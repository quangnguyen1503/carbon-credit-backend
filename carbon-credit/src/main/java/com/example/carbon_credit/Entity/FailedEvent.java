package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "failed_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedEvent {

    @Id
    private String id;

    @Column(name = "transaction_hash", nullable = false)
    private String transactionHash;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "contract_address")
    private String contractAddress;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "status")
    private String status;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

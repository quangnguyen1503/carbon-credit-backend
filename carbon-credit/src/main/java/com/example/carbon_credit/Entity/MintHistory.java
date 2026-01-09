package com.example.carbon_credit.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "mint_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MintHistory {

    @Id
    private String id;
    private String projectId;
    private String creditId;
    private String userId;
    private long mintAmount;
    private String txHash;
    private LocalDateTime createdAt;
}


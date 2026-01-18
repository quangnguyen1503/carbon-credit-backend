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
@Table(name = "carbon_credits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarbonCredit {
    @Id
    private String id;
    private String projectId;
    private long tokenId;
    private long totalAmount;
    private long issueAmount;
    private long retiredAmount;

}

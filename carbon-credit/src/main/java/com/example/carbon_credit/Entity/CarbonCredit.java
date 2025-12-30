package com.example.carbon_credit.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "carbon_credits")
@Data
public class CarbonCredit {
    @Id
    private String id;
    private String projectId;
    private String tokenId;
    private int totalAmount;
    private int issueAmount;
    private int retiredAmount;
    private String mintHash;
    private LocalDateTime issueAt;




}

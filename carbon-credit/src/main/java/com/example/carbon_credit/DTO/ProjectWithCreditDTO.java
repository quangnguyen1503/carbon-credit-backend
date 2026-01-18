package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectWithCreditDTO {
    // Project info
    private String id;
    private String name;
    private Integer vintage;
    private String ownerId;
    private String type;
    private String location;
    private String description;
    private String ipfsHash;
    private String status;
    private LocalDateTime createdAt;

    // Carbon Credit info (if minted)
    private Long tokenId;
    private Long totalAmount;
    private Long issueAmount;
    private Long retiredAmount;
    private Long availableAmount;
    private String methodology;

    // Trading info
    private Boolean isListed;
    private Boolean hasOrderBook;
}
package com.example.carbon_credit.DTO;

import lombok.Data;

@Data
public class ApprovedRequestDTO {
    private boolean approved;
    private String reason;
    private Long tokenId;
    private Long nftTokenId;
}


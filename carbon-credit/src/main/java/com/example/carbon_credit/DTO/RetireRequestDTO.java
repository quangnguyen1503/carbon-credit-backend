package com.example.carbon_credit.DTO;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RetireRequestDTO {
    private String projectId;
    private int amount;
    private String reason;
    private String tokenId;
    private String nftTokenId;
    private LocalDateTime retireAt;
}
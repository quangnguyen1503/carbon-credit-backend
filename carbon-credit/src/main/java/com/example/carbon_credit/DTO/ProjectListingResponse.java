package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectListingResponse {
    private String projectId;
    private String projectName;
    private Long tokenId;
    private String orderId;
    private String orderBookId;
    private BigDecimal initialPrice;
    private Integer amount;
    private String status;
    private LocalDateTime listedAt;
    private String message;
}
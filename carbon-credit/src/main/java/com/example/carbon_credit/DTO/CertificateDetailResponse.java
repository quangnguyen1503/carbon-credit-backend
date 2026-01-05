package com.example.carbon_credit.DTO;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CertificateDetailResponse {

    private String certificateId;
    private String userId;
    private String reason;
    private Integer totalAmount;
    private String status;

    private String onchainTxHash;
    private String nftTokenId;

    private LocalDateTime createdAt;

    private List<RecordDetail> records;

    @Data
    public static class RecordDetail {
        private String tokenId;
        private Integer amount;
    }
}


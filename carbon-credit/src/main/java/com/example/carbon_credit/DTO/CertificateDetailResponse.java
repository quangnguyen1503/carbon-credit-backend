package com.example.carbon_credit.DTO;

import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CertificateDetailResponse {

    private String certificateId;
    private String userId;
    private BigInteger totalAmount;

    private String onchainTxHash;
    private BigInteger nftTokenId;

    private LocalDateTime createdAt;

    private List<RecordDetail> records;

    @Data
    public static class RecordDetail {
        private BigInteger tokenId;
        private BigInteger amount;
    }
}


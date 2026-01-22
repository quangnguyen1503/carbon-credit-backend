package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CertificateDetailResponse {

    private String certificateId;
    private String projectName;
    private String userId;
    private BigInteger totalAmount;
    private String onchainTxHash;
    private BigInteger nftTokenId;
    private LocalDateTime createdAt;

    private List<RecordDetail> records;

    public CertificateDetailResponse(String certificateId, String projectName, String userId, BigInteger totalAmount, String onchainTxHash, BigInteger nftTokenId, LocalDateTime createdAt) {
        this.certificateId = certificateId;
        this.projectName = projectName;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.onchainTxHash = onchainTxHash;
        this.nftTokenId = nftTokenId;
        this.createdAt = createdAt;
    }

    @Data
    public static class RecordDetail {
        private String projectName;
        private BigInteger tokenId;
        private BigInteger amount;
    }

}


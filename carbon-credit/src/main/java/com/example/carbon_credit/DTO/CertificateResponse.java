package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CertificateResponse {
    private String certificateId;
    private String userId;
    private BigInteger totalAmount;
    private LocalDateTime createdAt;
}

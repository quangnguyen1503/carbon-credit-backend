package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CertificateResponse {
    private String certificateId;
    private String status;
    private Integer totalAmount;
    private LocalDateTime createdAt;
}

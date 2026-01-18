package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CertificateRecordDTO {
    private BigInteger certificateId;
    private BigInteger tokenId;
    private BigInteger amount;
}
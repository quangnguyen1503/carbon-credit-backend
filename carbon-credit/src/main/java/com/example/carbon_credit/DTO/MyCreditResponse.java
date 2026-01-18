package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MyCreditResponse {
    private String creditId;
    private Long tokenId;
    private String projectId;
    private BigInteger availableBalance;
}

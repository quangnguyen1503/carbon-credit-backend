package com.example.carbon_credit.DTO;

import lombok.Data;


@Data
public class MintRequestDTO {
    private long mintAmount;
    private String txHash;
}


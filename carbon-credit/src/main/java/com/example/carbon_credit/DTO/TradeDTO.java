package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TradeDTO {
    private String buyer;
    private String seller;
    private BigInteger creditTokenId;
    private BigInteger creditAmount;
    private BigInteger totalValue;

}
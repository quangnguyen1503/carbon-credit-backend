package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlockchainEventDTO {
    private String transactionHash;
    private BigInteger blockNumber;
    private String contractAddress;
    private String eventType;

    private List<String> topics;
    private String data;
}

package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MyCreditResponse {
    private String creditId;
    private long tokenId;
    private String projectId;
    private Integer balance;
}

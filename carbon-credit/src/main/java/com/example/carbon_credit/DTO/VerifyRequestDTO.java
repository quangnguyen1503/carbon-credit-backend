package com.example.carbon_credit.DTO;

import lombok.Data;

@Data
public class VerifyRequestDTO {
    private boolean approved;
    private int expectedCredits;
    private String reason;


}

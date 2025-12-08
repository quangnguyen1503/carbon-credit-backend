package com.example.carbon_credit.DTO;

import lombok.Data;

@Data
public class VerifyRequestDTO {
    private boolean approved;
    private String note;
}

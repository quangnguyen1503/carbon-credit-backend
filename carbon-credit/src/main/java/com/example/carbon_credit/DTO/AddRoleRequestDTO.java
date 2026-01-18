package com.example.carbon_credit.DTO;

import lombok.Data;

@Data
public class AddRoleRequestDTO {
    private String userId;
    private String roleName;
    private String verifierRoleId; // ✅ Thêm trường này (có thể null)
}
package com.example.carbon_credit.Entity;

import lombok.Data;

@Data
public class AddVerifierRequest {
    private String address;
    private String verifierRoleId;
}


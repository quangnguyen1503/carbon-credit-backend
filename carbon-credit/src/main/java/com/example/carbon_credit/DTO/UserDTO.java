package com.example.carbon_credit.DTO;

import lombok.Data;

@Data
public class UserDTO {
    private String id;
    private String name;
    private String roleId;
    private String email;
    private String documentHash;
    private String verifierRoleId;

    // Constructor (khớp AuthService)
    public UserDTO(String id, String name, String roleId, String documentHash, String verifierRoleId) {
        this.id = id;
        this.name = name;
        this.roleId = roleId;
        this.documentHash = documentHash;
        this.verifierRoleId = verifierRoleId;
    }


}
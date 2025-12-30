package com.example.carbon_credit.DTO;

import lombok.Data;

@Data
public class UserDTO {
    private String id;
    private String name;
    private String roleId;

    private String email;
    private String documentHash;

    // Constructor (khớp AuthService)
    public UserDTO(String id, String name, String roleId) {
        this.id = id;
        this.name = name;
        this.roleId = roleId;
    }


}
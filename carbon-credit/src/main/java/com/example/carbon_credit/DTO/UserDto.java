package com.example.carbon_credit.DTO;

public class UserDto {
    private String id;
    private String name;
    private String roleId;

    // Constructor (khớp AuthService)
    public UserDto(String id, String name, String roleId) {
        this.id = id;
        this.name = name;
        this.roleId = roleId;
    }

    // Getters/Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
}
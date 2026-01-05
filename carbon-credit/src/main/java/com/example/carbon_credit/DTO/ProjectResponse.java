package com.example.carbon_credit.DTO;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class ProjectResponse {
    private String id;
    private String name;
    private Integer vintage;
    private String location;
    private String type;
    private Integer expectedCredits;
    private String description;
    private String ipfsHash;
    private LocalDateTime createdAt;
    private String status;
    private String onchainHash;

    public ProjectResponse(String id, String name, Integer vintage, String location,
                           String type, Integer expectedCredits, String description, String ipfsHash,
                           LocalDateTime createdAt, String status, String onchainHash) {
        this.id = id;
        this.name = name;
        this.vintage = vintage;
        this.location = location;
        this.type = type;
        this.expectedCredits = expectedCredits;
        this.description = description;
        this.ipfsHash = ipfsHash;
        this.createdAt = createdAt;
        this.status = status;
        this.onchainHash = onchainHash;
    }


}

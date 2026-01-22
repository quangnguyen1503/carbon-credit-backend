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
    private long nftTokenId;
    private LocalDateTime createdAt;
    private String status;
    private String onchainHash;
    private long issueAmount;
    private long retiredAmount;
    private long tokenId;
    private String ownerName;
    private String verifyName;
    private String governmentName;

    public ProjectResponse(String id, String name, Integer vintage, String location,
                           String type, Integer expectedCredits, String description, String ipfsHash,long nftTokenId,
                           LocalDateTime createdAt, String status, String onchainHash, long issueAmount, long retiredAmount, String ownerName, String verifyName, String governmentName, long tokenId) {
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
        this.nftTokenId = nftTokenId;
        this.issueAmount = issueAmount;
        this.retiredAmount = retiredAmount;
        this.tokenId = tokenId;
        this.ownerName = ownerName;
        this.verifyName = verifyName;
        this.governmentName = governmentName;

    }


}

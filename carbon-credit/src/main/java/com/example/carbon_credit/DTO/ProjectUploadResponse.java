package com.example.carbon_credit.DTO;

import lombok.Data;

@Data
public class ProjectUploadResponse {
    private String imageUrl;
    private String docUrl;
    private String metadataHash; // Chỉ hash cho contract
    private String fullMetadataUri; // ipfs://Qm...
    private boolean success;
    private String message;
}

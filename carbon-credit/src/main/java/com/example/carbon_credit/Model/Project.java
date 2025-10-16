package com.example.carbon_credit.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "projects")
public class Project {

    @Id
    private String id;
    private String receiver;
    private String projectName;
    private long amountMinted;
    private String location;
    private String methodology;
    private String vintage;
    private String price;
    private String description;
    private String imageUrl;

    private String txHash;
    private String status;
    private Instant createdAt = Instant.now();

    // --- GETTERS & SETTERS ---
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getProjectName() {
        return projectName;
    }
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public long getAmountMinted() {
        return amountMinted;
    }
    public void setAmountMinted(long amountMinted) {
        this.amountMinted = amountMinted;
    }

    public String getLocation() {
        return location;
    }
    public void setLocation(String location) {
        this.location = location;
    }

    public String getMethodology() {
        return methodology;
    }
    public void setMethodology(String methodology) {
        this.methodology = methodology;
    }

    public String getVintage() {
        return vintage;
    }
    public void setVintage(String vintage) {
        this.vintage = vintage;
    }

    public String getPrice() {
        return price;
    }
    public void setPrice(String price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getReceiver() {
        return receiver;
    }
    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getTxHash() {
        return txHash;
    }
    public void setTxHash(String txHash) {
        this.txHash = txHash;
    }

    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

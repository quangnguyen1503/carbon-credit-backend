package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "retire_records")
@Data  // Lombok: Tạo getters/setters/constructor/equals/hashCode/toString
public class RetireRequest {

    @Id  // Primary key
    @GeneratedValue(strategy = GenerationType.UUID)  // Tự sinh UUID string (phù hợp varchar(255))
    @Column(name = "id", nullable = false, length = 255)
    private String id;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "project_id", nullable = false, length = 255)
    private String projectId;

    @Column(name = "token_id", nullable = true, length = 255)
    private String tokenId;  // ← THAY: Đổi tên field thành tokenId (camelCase) để rõ ràng, map với token_id

    // Nếu có field riêng 'tokenid' (hàng 15), thêm như này (giả sử nullable):
    // @Column(name = "tokenid", nullable = true, length = 255)
    // private String tokenid;

    @Column(name = "status", nullable = false, length = 255)
    private String status;  // PENDING | APPROVED | REJECTED | ONCHAIN_DONE

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "reason", nullable = true, length = 255)  // Có thể null
    private String reason;

    @Column(name = "retire_at", nullable = false)  // Not null theo schema
    private LocalDateTime retireAt;

    // Nếu 'tx_hash' là field riêng (not null), thêm:
    // @Column(name = "tx_hash", nullable = false, length = 255)
    // private String txHash;

    @Column(name = "onchain_tx_hash", nullable = true, length = 255)  // Có thể null
    private String onchainTxHash;

    @Column(name = "nft_token_id", nullable = true, length = 255)
    private String nftTokenId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "approved_at", nullable = true)
    private LocalDateTime approvedAt;

    @Column(name = "approved_by", nullable = true, length = 255)  // Có thể null
    private String approvedBy;
}
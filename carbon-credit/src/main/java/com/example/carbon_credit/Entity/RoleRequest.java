package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "role_requests")
@Data
public class RoleRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "requested_role")
    private String requestedRole;

    private String reason;

    private String status;

    @Column(name = "email_token")
    private String emailToken;

    @Column(name = "token_expired_at")
    private LocalDateTime tokenExpiredAt;

    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Version  // THÊM DÒNG NÀY: Optimistic locking để tránh conflict
    private Long version;  // THÊM FIELD NÀY: Auto-increment khi update
}
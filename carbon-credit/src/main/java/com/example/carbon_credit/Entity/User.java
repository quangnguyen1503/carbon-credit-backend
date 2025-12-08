package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", length = 255, unique = true, nullable = false)  // Map field "id" với cột DB "id" (varchar(255))
    private String id;  // Giá trị = address (e.g., "0x742d..."), không auto-generate

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "email", length = 255, unique = true)
    private String email;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "role_id")
    private String roleId;

    // SỬA create_at → created_at
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Giữ nguyên cột update_at theo đúng bảng của bạn
    @Column(name = "update_at")
    private LocalDateTime updateAt;
}

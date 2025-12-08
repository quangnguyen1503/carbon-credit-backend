package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @Column(name = "id", nullable = false)
    private String id;  // varchar(255)

    @Column(name = "name", nullable = false)
    private String name; // varchar(255)

    @Column(name = "vintage", nullable = false)
    private Integer vintage; // year(4) → dùng Integer

    @Column(name = "owner_id", nullable = false)
    private String ownerId; // varchar(255)

    @Column(name = "type", nullable = false)
    private String type; // varchar(255)

    @Column(name = "location", nullable = false)
    private String location; // varchar(255)

    @Column(name = "des", nullable = false, columnDefinition = "TEXT")
    private String description; // text

    @Column(name = "verified_by")
    private String verifiedBy; // varchar(255)

    @Column(name = "approved_by")
    private String approvedBy; // varchar(255)

    @Column(name = "expected_credits", nullable = false)
    private Integer expectedCredits; // int(11)

    @Column(name = "status", nullable = false)
    private String status; // varchar(20)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt; // datetime

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt; // datetime
}

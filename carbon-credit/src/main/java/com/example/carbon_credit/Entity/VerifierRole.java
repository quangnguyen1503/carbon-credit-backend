package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "verifier_roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifierRole {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "name", nullable = false, unique = true)
    private String organizationName;
    @Column(name = "description")
    private String description;
    @Version
    private Long version;

}

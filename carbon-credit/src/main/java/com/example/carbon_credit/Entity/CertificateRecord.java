package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "certificate_record")
@Data
public class CertificateRecord {

    @Id
    private String id;

    private String tokenId;
    private Integer amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id")
    private Certificate certificate;
}

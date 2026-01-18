package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigInteger;

@Entity
@Table(name = "certificate_record") // Nên đặt số nhiều cho tên bảng
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CertificateRecord {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "token_id", nullable = false)
    private BigInteger tokenId;

    @Column(name = "amount", nullable = false)
    private BigInteger amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id", nullable = false)
    @ToString.Exclude
    private Certificate certificate;
}
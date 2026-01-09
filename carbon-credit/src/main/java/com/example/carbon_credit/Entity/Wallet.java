package com.example.carbon_credit.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallets")
@Data
public class Wallet {
    @Id
    private String id;
    private String userId;
    private LocalDateTime updateAt;
}

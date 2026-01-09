package com.example.carbon_credit.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_credits")
@Data
public class WalletCredit {
    @Id
    private String id;
    private String walletId;
    private String creditId;
    private Integer balance;
    private LocalDateTime updateAt;
    private LocalDateTime creatAt;
}

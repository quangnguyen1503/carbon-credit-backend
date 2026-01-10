package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {
    @Id
    private String id; // Có thể dùng UUID.randomUUID().toString()

    @Column(name = "address", unique = true, nullable = false)
    private String address; // Địa chỉ ví 0x...

    @Column(name = "native_balance", precision = 38, scale = 18)
    private BigDecimal nativeBalance; // Số dư ETH có thể dùng

    @Column(name = "native_locked", precision = 38, scale = 18)
    private BigDecimal nativeLocked; // Số dư ETH đang bị khóa trong lệnh Buy

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version; // Optimistic Locking để tránh xung đột khi update đồng thời
}

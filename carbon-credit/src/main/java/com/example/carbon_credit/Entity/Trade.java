package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
@Entity
@Table(name = "trades")  // Tên bảng thực tế, thay nếu khác
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Nếu ID auto-increment, hoặc dùng @GeneratedValue nếu varchar manual
    @Column(name = "id", length = 255, nullable = false)
    private String id;  // varchar(255) - nếu manual, bỏ GeneratedValue

    @Column(name = "buy_order_id", length = 255)
    private String buyOrderId;

    @Column(name = "sell_order_id", length = 255)
    private String sellOrderId;

    @Column(name = "credit_id", length = 255)
    private String creditId;

    @Column(name = "price", precision = 10, scale = 0, nullable = false)
    private BigDecimal price;  // decimal(10,0)

    @Column(name = "amount", nullable = false)
    private Integer amount;  // int(11)

    @Column(name = "total_value", precision = 10, scale = 0, nullable = false)
    private BigDecimal totalValue;  // decimal(10,0)

    @Column(name = "tx_hash", length = 255)
    private String txHash;

    @Column(name = "trade_at")
    private LocalDateTime tradeAt;  // datetime
}


package com.example.carbon_credit.Entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")   // nếu bảng bạn tên khác thì sửa lại
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @Column(name = "id", nullable = false)
    private String id;  // varchar(255)

    @Column(name = "user_id", nullable = false)
    private String userId;  // varchar(255)

    @Column(name = "credit_id", nullable = false)
    private String creditId; // varchar(255)

    @Column(name = "order_type", nullable = false)
    private String orderType; // varchar(20) → BUY | SELL

    @Column(name = "price", nullable = false, precision = 10, scale = 0)
    private BigDecimal price;  // decimal(10,0)

    @Column(name = "amount", nullable = false)
    private Integer amount;  // int(11)

    @Column(name = "remaining_amount", nullable = false)
    private Integer remainingAmount;  // int(11)

    @Column(name = "status", nullable = false)
    private String status; // varchar(30)

    @Column(name = "created_at", nullable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;  // datetime
}

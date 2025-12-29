package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeEvent {
    private String tradeId;
    private String buyOrderId;
    private String sellOrderId;
    private String creditId;
    private Integer amount;
    private BigDecimal price;
    private BigDecimal totalValue;
    private LocalDateTime tradeAt;
}
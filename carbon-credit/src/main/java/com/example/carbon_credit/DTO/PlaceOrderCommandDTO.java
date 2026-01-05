package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderCommandDTO {
    private String orderId;
    private String userId;
    private String creditId;
    private String orderType;  // BUY | SELL
    private String orderCondition;
    private BigDecimal price;
    private Integer amount;
}
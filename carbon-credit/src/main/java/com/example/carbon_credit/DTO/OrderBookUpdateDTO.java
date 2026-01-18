package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookUpdateDTO {
    private String creditId;
    private BigDecimal bestBid;
    private BigDecimal bestAsk;
    private Integer bidVolume;
    private Integer askVolume;
    private List<OrderLevelDTO> bids;
    private List<OrderLevelDTO> asks;
    private LocalDateTime timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderLevelDTO {
        private BigDecimal price;
        private Integer amount;
    }
}
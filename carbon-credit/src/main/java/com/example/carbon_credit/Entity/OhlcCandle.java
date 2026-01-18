package com.example.carbon_credit.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ohlc_candles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OhlcCandle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String creditId;
    private String timeframe;  // 1m, 5m, 15m, 1h, 4h, 1d

    private Long timestamp;    // Unix timestamp (giây)
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
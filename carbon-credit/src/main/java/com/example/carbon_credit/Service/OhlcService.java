package com.example.carbon_credit.Service;

import com.example.carbon_credit.Entity.OhlcCandle;
import com.example.carbon_credit.Repository.OhlcCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OhlcService {

    private final OhlcCandleRepository ohlcRepository;
    private final String[] TIMEFRAMES = {"1m", "5m", "15m", "1h", "4h", "1d"};

    /**
     * Gọi sau mỗi trade để cập nhật tất cả timeframes
     */
    public void updateOhlcFromTrade(String creditId, BigDecimal price, Integer volume) {
        long now = Instant.now().getEpochSecond();

        for (String tf : TIMEFRAMES) {
            long interval = getIntervalSeconds(tf);
            long candleTime = (now / interval) * interval;

            updateOrCreateCandle(creditId, tf, candleTime, price, volume);
        }
    }

    private void updateOrCreateCandle(String creditId, String timeframe,
                                      Long timestamp, BigDecimal price, Integer volume) {
        Optional<OhlcCandle> existing = ohlcRepository
                .findByCreditIdAndTimeframeAndTimestamp(creditId, timeframe, timestamp);

        OhlcCandle candle;
        if (existing.isPresent()) {
            // Cập nhật nến hiện có
            candle = existing.get();
            candle.setHigh(candle.getHigh().max(price));
            candle.setLow(candle.getLow().min(price));
            candle.setClose(price);
            candle.setVolume(candle.getVolume() + volume);
            candle.setUpdatedAt(LocalDateTime.now());
        } else {
            // Tạo nến mới
            candle = OhlcCandle.builder()
                    .creditId(creditId)
                    .timeframe(timeframe)
                    .timestamp(timestamp)
                    .open(price)
                    .high(price)
                    .low(price)
                    .close(price)
                    .volume(Long.valueOf(volume))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        ohlcRepository.save(candle);
        log.debug("📊 Updated OHLC {}/{}: {}", creditId, timeframe, price);
    }

    /**
     * Lấy dữ liệu OHLC cho frontend
     */
    public List<OhlcCandle> getCandles(String creditId, String timeframe, int limit) {
        long interval = getIntervalSeconds(timeframe);
        long since = Instant.now().getEpochSecond() - (interval * limit);

        return ohlcRepository
                .findByCreditIdAndTimeframeAndTimestampGreaterThanOrderByTimestampAsc(
                        creditId, timeframe, since
                );
    }

    /**
     * Tính stats 24h
     */
    public Map<String, Object> get24hStats(String creditId) {
        long since24h = Instant.now().getEpochSecond() - 86400;
        List<OhlcCandle> candles = ohlcRepository
                .findByCreditIdAndTimeframeAndTimestampGreaterThanOrderByTimestampAsc(
                        creditId, "1m", since24h
                );

        if (candles.isEmpty()) {
            return Map.of(
                    "currentPrice", BigDecimal.ZERO,
                    "priceChange", BigDecimal.ZERO,
                    "priceChangePercent", BigDecimal.ZERO,
                    "high24h", BigDecimal.ZERO,
                    "low24h", BigDecimal.ZERO,
                    "volume24h", 0L
            );
        }

        OhlcCandle first = candles.get(0);
        OhlcCandle last = candles.get(candles.size() - 1);

        BigDecimal high24h = candles.stream()
                .map(OhlcCandle::getHigh)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal low24h = candles.stream()
                .map(OhlcCandle::getLow)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        Long volume24h = candles.stream()
                .mapToLong(OhlcCandle::getVolume)
                .sum();

        BigDecimal priceChange = last.getClose().subtract(first.getOpen());
        BigDecimal priceChangePercent = first.getOpen().compareTo(BigDecimal.ZERO) > 0
                ? priceChange.divide(first.getOpen(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return Map.of(
                "currentPrice", last.getClose(),
                "priceChange", priceChange,
                "priceChangePercent", priceChangePercent,
                "high24h", high24h,
                "low24h", low24h,
                "volume24h", volume24h
        );
    }

    private long getIntervalSeconds(String tf) {
        return switch (tf) {
            case "1m" -> 60;
            case "5m" -> 300;
            case "15m" -> 900;
            case "1h" -> 3600;
            case "4h" -> 14400;
            case "1d" -> 86400;
            default -> 60;
        };
    }
}
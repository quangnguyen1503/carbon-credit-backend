package com.example.carbon_credit.Controller;

import com.example.carbon_credit.Entity.OhlcCandle;
import com.example.carbon_credit.Entity.Trade;
import com.example.carbon_credit.Repository.TradeRepository;
import com.example.carbon_credit.Service.OhlcService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MarketController {

    private final OhlcService ohlcService;

    @GetMapping("/ohlc/{creditId}")
    public ResponseEntity<?> getOhlcData(
            @PathVariable String creditId,
            @RequestParam(defaultValue = "15m") String timeframe,
            @RequestParam(defaultValue = "100") int limit
    ) {
        List<OhlcCandle> candles = ohlcService.getCandles(creditId, timeframe, limit);
        Map<String, Object> stats = ohlcService.get24hStats(creditId);

        // Chuyển đổi sang format frontend cần
        List<Map<String, Object>> candleData = candles.stream()
                .map(c -> Map.<String, Object>of(
                        "timestamp", c.getTimestamp(),
                        "open", c.getOpen(),
                        "high", c.getHigh(),
                        "low", c.getLow(),
                        "close", c.getClose(),
                        "volume", c.getVolume()
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "candles", candleData,
                "stats", stats
        ));
    }

    private final TradeRepository tradeRepository;

    @GetMapping("/recent/{creditId}")
    public ResponseEntity<List<Trade>> getRecentTrades(
            @PathVariable String creditId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<Trade> trades = tradeRepository
                .findByCreditIdOrderByTradeAtDesc(creditId, PageRequest.of(0, limit));

        return ResponseEntity.ok(trades);
    }
}
package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.TradeEventDTO;
import com.example.carbon_credit.Entity.Trade;
import com.example.carbon_credit.Repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeRecoveryService {

    private final TradeRepository tradeRepository;
    private final SettlementService settlementService;

    private static final int STUCK_THRESHOLD_MINUTES = 5;

    /**
     * Scheduled task to recover stuck trades every 2 minutes
     */
    @Scheduled(fixedDelay = 120000) // 2 minutes
    public void recoverStuckTrades() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);

        List<Trade> stuckTrades = tradeRepository.findByStatusAndTradeAtBefore(
                "PENDING_SETTLEMENT", threshold);

        if (stuckTrades.isEmpty()) {
            return;
        }

        log.warn("Found {} stuck trades. Attempting recovery...", stuckTrades.size());

        for (Trade trade : stuckTrades) {
            try {
                recoverTrade(trade);
            } catch (Exception e) {
                log.error("Failed to recover trade {}: {}", trade.getId(), e.getMessage());
            }
        }
    }

    private void recoverTrade(Trade trade) {
        TradeEventDTO tradeEvent = TradeEventDTO.builder()
                .tradeId(trade.getId())
                .buyOrderId(trade.getBuyOrderId())
                .sellOrderId(trade.getSellOrderId())
                .creditId(trade.getCreditId())
                .amount(trade.getAmount())
                .price(trade.getPrice())
                .totalValue(trade.getTotalValue())
                .tradeAt(trade.getTradeAt())
                .build();

        settlementService.addTradeToBatch(tradeEvent);

        log.info("Trade {} re-added to settlement batch", trade.getId());
    }
}

package com.example.carbon_credit.Kafka;

import com.example.carbon_credit.DTO.PlaceOrderCommandDTO;
import com.example.carbon_credit.DTO.TradeEventDTO;
import com.example.carbon_credit.MatchingEngine.MatchingEngine;
import com.example.carbon_credit.Service.PersistenceService;
import com.example.carbon_credit.Service.SettlementService;
import com.example.carbon_credit.Service.WsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final MatchingEngine matchingEngine;
    private final KafkaProducerService kafkaProducerService;
    private final WsService wsService;
    private final SettlementService settlementService;
    private final PersistenceService persistenceService;

    /**
     * Consumer 1: Nhận lệnh từ topic "orders" → Khớp lệnh
     */
    @KafkaListener(
            topics = "orders",
            groupId = "carbon-market-group",
            concurrency = "3"
    )
    public void consumeOrder(PlaceOrderCommandDTO command, Acknowledgment ack) {
        boolean isMarketOrder = command.getPrice().compareTo(BigDecimal.ZERO) == 0;
        String orderTypeDisplay = isMarketOrder ? "MARKET" : "LIMIT";
        
        log.info("📥 Received {} order: {} {} {} @ {}", 
                orderTypeDisplay,
                command.getOrderType(), 
                command.getAmount(), 
                command.getCreditId(), 
                isMarketOrder ? "MARKET" : command.getPrice());

        try {
            // ⚙️ GỌI MATCHING ENGINE
            List<TradeEventDTO> trades = matchingEngine.processOrder(command);

            // 📤 Nếu có trades → Gửi vào topic "trades"
            if (trades != null && !trades.isEmpty()) {
                log.info("✅ Matched {} trades for order {}", trades.size(), command.getOrderId());
                kafkaProducerService.sendTrades(trades);
                
                // 📡 Broadcast orderbook update
                broadcastOrderBookChange(command.getCreditId());
            } else {
                if (isMarketOrder) {
                    log.warn("⚠️ Market order {} has no liquidity", command.getOrderId());
                } else {
                    log.info("⏳ Limit order {} added to book", command.getOrderId());
                    
                    // 📡 Broadcast orderbook update (order added to book)
                    broadcastOrderBookChange(command.getCreditId());
                }
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error("❌ Error processing order {}: {}", command.getOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Consumer 2: Nhận trades từ topic "trades" → Xử lý sau giao dịch
     */
    @KafkaListener(
            topics = "trades",
            groupId = "carbon-market-group"
    )
    public void consumeTrade(TradeEventDTO trade, Acknowledgment ack) {
        log.info("💰 Processing trade: {} amount={} price={}", 
                trade.getTradeId(), 
                trade.getAmount(), 
                trade.getPrice());

        try {
            // 1️⃣ Broadcast trade realtime qua WebSocket
            wsService.broadcastTrade(trade);
            
            // 2️⃣ Broadcast price update
            wsService.broadcastPriceUpdate(
                trade.getCreditId(), 
                trade.getPrice(), 
                trade.getAmount()
            );

            // 3️⃣ Add to settlement batch
            settlementService.addTradeToBatch(trade);

            // 4️⃣ Save to database
            persistenceService.saveHistoricalTrade(trade);

            ack.acknowledge();
            log.info("✅ Trade {} processed successfully", trade.getTradeId());

        } catch (Exception e) {
            log.error("❌ Error processing trade {}: {}", trade.getTradeId(), e.getMessage(), e);
        }
    }

    /**
     * Helper: Broadcast orderbook snapshot khi có thay đổi
     */
    private void broadcastOrderBookChange(String creditId) {
        Map<String, Object> snapshot = matchingEngine.getOrderBook(creditId);
        if (snapshot != null) {
            wsService.broadcastOrderBookUpdate(creditId, snapshot);
        }
    }
}

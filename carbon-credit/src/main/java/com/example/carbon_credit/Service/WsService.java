package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.TradeEvent;
import com.example.carbon_credit.DTO.OrderBookUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WsService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcast trade event khi có giao dịch mới
     */
    public void broadcastTrade(TradeEvent trade) {
        // Gửi đến topic specific cho credit
        messagingTemplate.convertAndSend(
            "/topic/trades/" + trade.getCreditId(),
            trade
        );
        
        // Gửi đến topic chung (tất cả credits)
        messagingTemplate.convertAndSend("/topic/trades/all", trade);
        
        log.info("📡 Broadcasted trade {} via WebSocket", trade.getTradeId());
    }

    /**
     * Broadcast orderbook update khi có thay đổi
     */
    public void broadcastOrderBookUpdate(String creditId, Map<String, Object> snapshot) {
        OrderBookUpdate update = OrderBookUpdate.builder()
            .creditId(creditId)
            .bestBid((BigDecimal) snapshot.get("bestBid"))
            .bestAsk((BigDecimal) snapshot.get("bestAsk"))
            .bidVolume((Integer) snapshot.get("bestBidVolume"))
            .askVolume((Integer) snapshot.get("bestAskVolume"))
            .timestamp(LocalDateTime.now())
            .build();
        
        messagingTemplate.convertAndSend(
            "/topic/orderbook/" + creditId,
            update
        );
        
        log.debug("📊 Broadcasted orderbook update for {}", creditId);
    }

    /**
     * Broadcast price update (last traded price)
     */
    public void broadcastPriceUpdate(String creditId, BigDecimal price, int volume) {
        Map<String, Object> priceUpdate = Map.of(
            "creditId", creditId,
            "price", price,
            "volume", volume,
            "timestamp", LocalDateTime.now()
        );
        
        messagingTemplate.convertAndSend(
            "/topic/price/" + creditId,
            priceUpdate
        );
        
        log.debug("💲 Broadcasted price update: {} @ {}", creditId, price);
    }
}
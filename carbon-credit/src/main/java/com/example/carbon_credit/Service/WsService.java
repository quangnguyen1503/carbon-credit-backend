package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.OrderNotificationDTO;
import com.example.carbon_credit.DTO.TradeEventDTO;
import com.example.carbon_credit.DTO.OrderBookUpdateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WsService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcast trade event khi có giao dịch mới
     */
    public void broadcastTrade(TradeEventDTO trade) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/trades/" + trade.getCreditId(),
                    trade);

            messagingTemplate.convertAndSend("/topic/trades/all", trade);

            log.info("📡 Broadcasted trade {} via WebSocket", trade.getTradeId());
        } catch (Exception e) {
            log.error("❌ Failed to broadcast trade: {}", e.getMessage());
        }
    }

    /**
     * Broadcast orderbook update khi có thay đổi
     */
    public void broadcastOrderBookUpdate(String creditId, Map<String, Object> snapshot) {
        try {
            // Safe conversion for lists
            List<Map<String, Object>> rawBids = (List<Map<String, Object>>) snapshot.getOrDefault("bids",
                    new ArrayList<>());
            List<Map<String, Object>> rawAsks = (List<Map<String, Object>>) snapshot.getOrDefault("asks",
                    new ArrayList<>());

            List<OrderBookUpdateDTO.OrderLevelDTO> bids = rawBids.stream()
                    .map(m -> new OrderBookUpdateDTO.OrderLevelDTO(
                            getBigDecimal(m.get("price")),
                            getInteger(m.get("amount"))))
                    .collect(Collectors.toList());

            List<OrderBookUpdateDTO.OrderLevelDTO> asks = rawAsks.stream()
                    .map(m -> new OrderBookUpdateDTO.OrderLevelDTO(
                            getBigDecimal(m.get("price")),
                            getInteger(m.get("amount"))))
                    .collect(Collectors.toList());

            OrderBookUpdateDTO update = OrderBookUpdateDTO.builder()
                    .creditId(creditId)
                    .bestBid(getBigDecimal(snapshot.get("bestBid")))
                    .bestAsk(getBigDecimal(snapshot.get("bestAsk")))
                    .bidVolume(getInteger(snapshot.get("bestBidVolume")))
                    .askVolume(getInteger(snapshot.get("bestAskVolume")))
                    .bids(bids)
                    .asks(asks)
                    .timestamp(LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSend(
                    "/topic/orderbook/" + creditId,
                    update);

            log.debug("📊 Broadcasted orderbook update for {}", creditId);
        } catch (Exception e) {
            log.error("❌ Failed to broadcast orderbook update for {}: {}", creditId, e.getMessage(), e);
        }
    }

    /**
     * Broadcast price update (last traded price)
     */
    public void broadcastPriceUpdate(String creditId, BigDecimal price, int volume) {
        try {
            Map<String, Object> priceUpdate = Map.of(
                    "creditId", creditId,
                    "price", price,
                    "volume", volume,
                    "timestamp", LocalDateTime.now());

            messagingTemplate.convertAndSend(
                    "/topic/price/" + creditId,
                    priceUpdate);

            log.debug("💲 Broadcasted price update: {} @ {}", creditId, price);
        } catch (Exception e) {
            log.error("❌ Failed to broadcast price update: {}", e.getMessage());
        }
    }

    public void notifyOrderFailure(String userId, String orderId, String creditId, String reason) {
        try {
            OrderNotificationDTO notification = OrderNotificationDTO.builder()
                    .type("ORDER_FAILED")
                    .orderId(orderId)
                    .creditId(creditId)
                    .message("Order cancelled due to system error: " + reason)
                    .timestamp(System.currentTimeMillis())
                    .build();

            // Spring sẽ map thành: /user/{userId}/queue/errors
            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/errors",
                    notification);

            log.info("Sent failure notification to user {}", userId);

        } catch (Exception e) {
            log.error("Failed to send WS notification: {}", e.getMessage());
        }
    }

    /**
     * Gửi thông báo hủy lệnh thành công cho user
     */
    public void notifyOrderCancelled(String userId, String orderId, String creditId) {
        try {
            OrderNotificationDTO notification = OrderNotificationDTO.builder()
                    .type("ORDER_CANCELLED")
                    .orderId(orderId)
                    .creditId(creditId)
                    .message("Order cancelled successfully")
                    .timestamp(System.currentTimeMillis())
                    .build();

            // Gửi đến kênh riêng của user
            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/orders",
                    notification);

            log.info("🔔 Sent cancel notification to user {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to send cancel notification: {}", e.getMessage());
        }
    }

    /**
     * Gửi thông báo lệnh hết hạn cho user
     */
    public void notifyOrderExpired(String userId, String orderId, String creditId) {
        try {
            OrderNotificationDTO notification = OrderNotificationDTO.builder()
                    .type("ORDER_EXPIRED")
                    .orderId(orderId)
                    .creditId(creditId)
                    .message("Order expired because it exceeded 24 hours")
                    .timestamp(System.currentTimeMillis())
                    .build();

            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/orders",
                    notification);

            log.info("🔔 Sent expired notification to user {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to send expired notification: {}", e.getMessage());
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Helper: Chuyển đổi object sang BigDecimal an toàn
     */
    private BigDecimal getBigDecimal(Object value) {
        if (value == null)
            return BigDecimal.ZERO;
        if (value instanceof BigDecimal)
            return (BigDecimal) value;
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException e) {
                log.warn("⚠️ Invalid number format for BigDecimal: {}", value);
                return BigDecimal.ZERO;
            }
        }
        if (value instanceof Integer)
            return BigDecimal.valueOf((Integer) value);
        if (value instanceof Double)
            return BigDecimal.valueOf((Double) value);
        if (value instanceof Long)
            return BigDecimal.valueOf((Long) value);

        return BigDecimal.ZERO;
    }

    /**
     * Helper: Chuyển đổi object sang Integer an toàn
     */
    private Integer getInteger(Object value) {
        if (value == null)
            return 0;
        if (value instanceof Integer)
            return (Integer) value;
        if (value instanceof Number)
            return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                log.warn("⚠️ Invalid number format for Integer: {}", value);
                return 0;
            }
        }
        return 0;
    }
}
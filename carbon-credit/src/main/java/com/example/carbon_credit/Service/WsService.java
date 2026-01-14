package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.TradeEventDTO;
import com.example.carbon_credit.DTO.OrderBookUpdateDTO;
import com.example.carbon_credit.Entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WsService {

    private final SimpMessagingTemplate messagingTemplate;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 1. THÔNG BÁO THỊ TRƯỜNG (MARKET DATA) - Dành cho tất cả User
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Broadcast trade event khi có giao dịch mới khớp lệnh thành công
     */
    public void broadcastTrade(TradeEventDTO trade) {
        // Gửi đến topic cụ thể cho loại credit đó
        messagingTemplate.convertAndSend(
                "/topic/trades/" + trade.getCreditId(),
                trade
        );

        // Gửi đến topic chung cho tất cả giao dịch trên sàn
        messagingTemplate.convertAndSend("/topic/trades/all", trade);

        log.info("📡 Broadcasted trade {} via WebSocket", trade.getTradeId());
    }

    public void broadcastSnapshot(List<Order> buyOrders, List<Order> sellOrders) {
        Map<String, Object> payload = Map.of(
                "type", "SNAPSHOT",
                "orders", List.of(buyOrders, sellOrders),
                "timestamp", LocalDateTime.now()
        );

        // Bạn có thể gửi vào topic chung hoặc topic theo creditId tùy nhu cầu
        messagingTemplate.convertAndSend("/topic/orderbook", payload);

        log.info("📊 Broadcasted Orderbook Snapshot: {} buys, {} sells", buyOrders.size(), sellOrders.size());
    }

    /**
     * Broadcast cập nhật Orderbook (Giá mua/bán tốt nhất)
     */
    public void broadcastOrderBookUpdate(String creditId, Map<String, Object> snapshot) {
        OrderBookUpdateDTO update = OrderBookUpdateDTO.builder()
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
     * Broadcast cập nhật giá khớp lệnh cuối cùng (Last Price)
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

    /**
     * Cập nhật trạng thái lệnh (Dùng để FE xóa lệnh khỏi bảng hoặc update số lượng khớp một phần)
     */
    public void broadcastOrderUpdate(Order order) {
        String destination = "/topic/orderbook/" + order.getCreditId();
        if ("FILLED".equals(order.getStatus()) || "CANCELLED".equals(order.getStatus())) {
            // Lệnh đã xong hoặc bị hủy -> Xóa khỏi UI
            messagingTemplate.convertAndSend(destination, Map.of("type", "REMOVE", "orderId", order.getId()));
        } else {
            // Lệnh khớp một phần -> Cập nhật số lượng còn lại
            messagingTemplate.convertAndSend(destination, Map.of("type", "UPDATE", "order", order));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 2. THÔNG BÁO CÁ NHÂN (USER DATA) - Chỉ gửi cho đúng chủ ví
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Cập nhật số dư ví (Native ETH hoặc Credit Token)
     * Rất quan trọng cho luồng Nạp/Rút đồng bộ với Blockchain
     */
    public void broadcastBalanceUpdate(String walletAddress, String assetType, Object balance) {
        // Đảm bảo địa chỉ ví viết thường để FE dễ bắt
        String destination = "/topic/wallet/" + walletAddress.toLowerCase();

        Map<String, Object> payload = Map.of(
                "type", "BALANCE_UPDATE",
                "asset", assetType, // NATIVE hoặc CREDIT
                "balance", balance,
                "timestamp", LocalDateTime.now()
        );

        messagingTemplate.convertAndSend(destination, payload);
        log.info("💰 Sent balance update to wallet: {} (Asset: {})", walletAddress, assetType);
    }

    /**
     * Gửi thông báo lỗi hoặc thông báo hệ thống riêng cho User
     */
    public void sendPrivateNotification(String walletAddress, String message) {
        String destination = "/topic/wallet/" + walletAddress.toLowerCase();
        messagingTemplate.convertAndSend(destination, Map.of(
                "type", "NOTIFICATION",
                "message", message,
                "timestamp", LocalDateTime.now()
        ));
    }
}
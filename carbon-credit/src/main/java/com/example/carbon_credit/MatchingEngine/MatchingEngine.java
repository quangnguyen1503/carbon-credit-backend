package com.example.carbon_credit.MatchingEngine;

import com.example.carbon_credit.DTO.PlaceOrderCommandDTO;
import com.example.carbon_credit.DTO.TradeEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class MatchingEngine {

    // Map: creditId → OrderBook instance
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    /**
     * Xử lý lệnh: Định tuyến đến đúng OrderBook và thực hiện khớp lệnh
     */
    public List<TradeEventDTO> processOrder(PlaceOrderCommandDTO command) {
        String creditId = command.getCreditId();

        // Validate command
        if (!validateOrder(command)) {
            log.error("Invalid order: {}", command);
            return new ArrayList<>();
        }

        // Lấy hoặc tạo OrderBook cho creditId
        OrderBook orderBook = orderBooks.computeIfAbsent(creditId, k -> {
            log.info("Creating new OrderBook for creditId: {}", k);
            return new OrderBook(k);
        });

        log.debug("OrderBook created/retrieved: {}", orderBook.getCreditId());

        // Handle Market Orders (price = 0)
        boolean isMarketOrder = command.getPrice().compareTo(BigDecimal.ZERO) == 0;
        if (isMarketOrder) {
            handleMarketOrder(command);
        }

        // Thực hiện thuật toán khớp lệnh
        List<TradeEventDTO> trades = orderBook.matchOrder(command);

        if (trades.isEmpty()) {
            log.debug("Order {} added to book, no immediate match", command.getOrderId());
        } else {
            log.info("Order {} matched {} trades", command.getOrderId(), trades.size());
        }

        return trades;
    }

    /**
     * Cancel order
     */
    public boolean cancelOrder(String creditId, String orderId) {
        OrderBook orderBook = orderBooks.get(creditId);
        if (orderBook == null) {
            log.warn("❌ OrderBook not found for creditId: {}", creditId);
            return false;
        }

        boolean removed = orderBook.removeOrder(orderId);
        if (removed) {
            log.info("✅ Order {} cancelled successfully", orderId);
            return true;
        }

        log.warn("❌ Order {} not found for cancellation", orderId);
        return false;
    }

    /**
     * Lấy snapshot của một OrderBook (cho debug/monitoring)
     */
    public Map<String, Object> getOrderBook(String creditId) {
        OrderBook orderBook = orderBooks.get(creditId);
        return orderBook != null ? orderBook.getSnapshot() : null;
    }

    /**
     * Get all orderbooks snapshot
     */
    public Map<String, Map<String, Object>> getAllOrderBooks() {
        Map<String, Map<String, Object>> snapshots = new ConcurrentHashMap<>();
        orderBooks.forEach((creditId, orderBook) -> snapshots.put(creditId, orderBook.getSnapshot()));
        return snapshots;
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Validate order basic requirements
     */
    private boolean validateOrder(PlaceOrderCommandDTO command) {
        if (command == null) {
            log.error("Order command is null");
            return false;
        }

        if (command.getOrderId() == null || command.getOrderId().isEmpty()) {
            log.error("Order ID is required");
            return false;
        }

        if (command.getCreditId() == null || command.getCreditId().isEmpty()) {
            log.error("Credit ID is required");
            return false;
        }

        if (command.getAmount() == null || command.getAmount() <= 0) {
            log.error("Amount must be positive");
            return false;
        }

        if (command.getPrice() == null || command.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            log.error("Price must be non-negative");
            return false;
        }

        String orderType = command.getOrderType();
        if (!"BUY".equalsIgnoreCase(orderType) && !"SELL".equalsIgnoreCase(orderType)) {
            log.error("Invalid order type: {}", orderType);
            return false;
        }

        return true;
    }

    /**
     * Handle market orders (price = 0)
     * Market order = Limit order với giá cực đoan để đảm bảo khớp ngay
     */
    private void handleMarketOrder(PlaceOrderCommandDTO command) {
        boolean isBuy = "BUY".equalsIgnoreCase(command.getOrderType());

        // Set price to guarantee immediate match
        // Buy: giá cực cao để ăn hết ask orders
        // Sell: giá 0.01 để ăn hết bid orders
        command.setPrice(isBuy ? new BigDecimal("999999999") : new BigDecimal("0.01"));

        log.info("📊 Market order converted: {} @ price={}", command.getOrderType(), command.getPrice());
    }

    public boolean hasOrderBook(String creditId) {
        return orderBooks.containsKey(creditId);
    }

    public void createOrderBook(String creditId) {
        if (!orderBooks.containsKey(creditId)) {
            log.info("📊 Creating new OrderBook for creditId: {}", creditId);
            orderBooks.put(creditId, new OrderBook(creditId));
        }
    }

    public Map<String, Object> getOrderBookSnapshot(String creditId) {
        OrderBook orderBook = orderBooks.get(creditId);
        if (orderBook == null) {
            return null;
        }

        return orderBook.getSnapshot();
    }
}
package com.example.carbon_credit.MatchingEngine;

import com.example.carbon_credit.DTO.PlaceOrderCommandDTO;
import com.example.carbon_credit.DTO.TradeEventDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Getter
public class OrderBook {

    private final String creditId;

    // Heaps for fast price lookups
    private final IndexedHeap<BigDecimal> bidHeap;  // Max heap for bids (highest first)
    private final IndexedHeap<BigDecimal> askHeap;  // Min heap for asks (lowest first)

    // Price levels: Price → LinkedList of orders
    private final Map<BigDecimal, BidPriceLevel> bidLevels;
    private final Map<BigDecimal, AskPriceLevel> askLevels;

    // Order index: OrderId → OrderNode (for O(1) lookups)
    private final Map<String, OrderNode> orderIndex;

    // Tracking remaining amounts
    private final Map<String, Integer> remainingAmounts;

    // Cache for sorted prices (invalidated on add/remove)
    private List<BigDecimal> sortedBidPrices;
    private List<BigDecimal> sortedAskPrices;
    private boolean bidCacheDirty = true;
    private boolean askCacheDirty = true;

    public OrderBook(String creditId) {
        this.creditId = creditId;
        this.bidHeap = new IndexedHeap<>(true);   // Max heap
        this.askHeap = new IndexedHeap<>(false);  // Min heap
        this.bidLevels = new ConcurrentHashMap<>();
        this.askLevels = new ConcurrentHashMap<>();
        this.orderIndex = new ConcurrentHashMap<>();
        this.remainingAmounts = new ConcurrentHashMap<>();
    }

    // ==================== PUBLIC METHODS ====================

    /**
     * Add order to orderbook
     */
    public synchronized void addOrder(PlaceOrderCommandDTO order) {
        OrderNode orderNode = new OrderNode(order);
        orderIndex.put(order.getOrderId(), orderNode);
        remainingAmounts.put(order.getOrderId(), order.getAmount());

        if ("BUY".equalsIgnoreCase(order.getOrderType())) {
            addToBidLevel(order, orderNode);
        } else {
            addToAskLevel(order, orderNode);
        }

        log.debug("📝 Added {} order {} @ {} to orderbook", 
                order.getOrderType(), order.getOrderId(), order.getPrice());
    }

    /**
     * Match incoming order against orderbook
     * Returns list of trades generated
     */
    public synchronized List<TradeEventDTO> matchOrder(PlaceOrderCommandDTO newOrder) {
        List<TradeEventDTO> trades = new ArrayList<>();
        int remaining = newOrder.getAmount();
        remainingAmounts.put(newOrder.getOrderId(), remaining);

        boolean isBuy = "BUY".equalsIgnoreCase(newOrder.getOrderType());

        log.debug("🔍 Matching {} order {} @ {} (remaining: {})",
                newOrder.getOrderType(), newOrder.getOrderId(), newOrder.getPrice(), remaining);

        // Get opposite side price levels
        while (remaining > 0) {
            BigDecimal bestOppositePrice = isBuy ? getBestAskPrice() : getBestBidPrice();
            if (bestOppositePrice == null) {
                log.debug("❌ No opposite orders available");
                break;
            }

            // Check price cross
            boolean priceCross = isBuy
                    ? newOrder.getPrice().compareTo(bestOppositePrice) >= 0  // Buy price >= sell price
                    : newOrder.getPrice().compareTo(bestOppositePrice) <= 0; // Sell price <= buy price

            if (!priceCross) {
                log.debug("❌ No price cross: {} vs {}", newOrder.getPrice(), bestOppositePrice);
                break;
            }

            // Get price level
            PriceLevel priceLevel = isBuy ? askLevels.get(bestOppositePrice) : bidLevels.get(bestOppositePrice);
            if (priceLevel == null || priceLevel.getOrders().isEmpty()) {
                // Remove empty level
                if (isBuy) {
                    askHeap.remove(bestOppositePrice);
                    askLevels.remove(bestOppositePrice);
                    invalidateAskCache();
                } else {
                    bidHeap.remove(bestOppositePrice);
                    bidLevels.remove(bestOppositePrice);
                    invalidateBidCache();
                }
                continue;
            }

            // Match with orders at this price level
            Iterator<PlaceOrderCommandDTO> iterator = priceLevel.getOrders().iterator();
            while (iterator.hasNext() && remaining > 0) {
                PlaceOrderCommandDTO oppOrder = iterator.next();
                int oppRemaining = remainingAmounts.getOrDefault(oppOrder.getOrderId(), oppOrder.getAmount());

                if (oppRemaining <= 0) {
                    iterator.remove();
                    orderIndex.remove(oppOrder.getOrderId());
                    continue;
                }

                // Calculate match amount
                int matchAmount = Math.min(remaining, oppRemaining);

                // Create trade
                TradeEventDTO trade = TradeEventDTO.builder()
                        .tradeId(UUID.randomUUID().toString())
                        .buyOrderId(isBuy ? newOrder.getOrderId() : oppOrder.getOrderId())
                        .sellOrderId(isBuy ? oppOrder.getOrderId() : newOrder.getOrderId())
                        .creditId(creditId)
                        .amount(matchAmount)
                        .price(bestOppositePrice)  // Trade at maker price
                        .totalValue(bestOppositePrice.multiply(BigDecimal.valueOf(matchAmount)))
                        .tradeAt(LocalDateTime.now())
                        .build();

                trades.add(trade);

                // Update remaining amounts
                remaining -= matchAmount;
                oppRemaining -= matchAmount;

                remainingAmounts.put(newOrder.getOrderId(), remaining);
                remainingAmounts.put(oppOrder.getOrderId(), oppRemaining);

                log.info("✅ Matched: {} x {} @ {}", matchAmount, creditId, bestOppositePrice);

                // Remove filled order
                if (oppRemaining <= 0) {
                    iterator.remove();
                    orderIndex.remove(oppOrder.getOrderId());
                    remainingAmounts.remove(oppOrder.getOrderId());
                }
            }

            // Update price level total volume
            priceLevel.recalculateTotalVolume(remainingAmounts);

            // Remove empty price level
            if (priceLevel.getOrders().isEmpty()) {
                if (isBuy) {
                    askHeap.remove(bestOppositePrice);
                    askLevels.remove(bestOppositePrice);
                    invalidateAskCache();
                } else {
                    bidHeap.remove(bestOppositePrice);
                    bidLevels.remove(bestOppositePrice);
                    invalidateBidCache();
                }
            }
        }

        // Add remaining order to book
        if (remaining > 0) {
            addOrder(newOrder);
        }

        return trades;
    }

    /**
     * Remove order from orderbook
     */
    public synchronized boolean removeOrder(String orderId) {
        OrderNode orderNode = orderIndex.get(orderId);
        if (orderNode == null) {
            return false;
        }

        PlaceOrderCommandDTO order = orderNode.getOrder();
        if ("BUY".equalsIgnoreCase(order.getOrderType())) {
            removeFromBidLevel(order);
        } else {
            removeFromAskLevel(order);
        }

        orderIndex.remove(orderId);
        remainingAmounts.remove(orderId);
        log.debug("🗑️ Removed order {} from orderbook", orderId);
        return true;
    }

    /**
     * Find order by ID
     */
    public PlaceOrderCommandDTO findOrder(String orderId) {
        OrderNode node = orderIndex.get(orderId);
        return node != null ? node.getOrder() : null;
    }

    // ==================== GETTERS ====================

    public BigDecimal getBestBidPrice() {
        return bidHeap.isEmpty() ? null : bidHeap.peek();
    }

    public BigDecimal getBestAskPrice() {
        return askHeap.isEmpty() ? null : askHeap.peek();
    }

    public Integer getBestBidVolume() {
        BigDecimal price = getBestBidPrice();
        return price != null ? bidLevels.get(price).getTotalVolume() : null;
    }

    public Integer getBestAskVolume() {
        BigDecimal price = getBestAskPrice();
        return price != null ? askLevels.get(price).getTotalVolume() : null;
    }

    public int getTotalOrders() {
        return orderIndex.size();
    }

    // ==================== SNAPSHOT ====================

    public Map<String, Object> getSnapshot() {
        return Map.of(
                "creditId", creditId,
                "bestBid", getBestBidPrice() != null ? getBestBidPrice() : "N/A",
                "bestAsk", getBestAskPrice() != null ? getBestAskPrice() : "N/A",
                "bidLevels", bidLevels.size(),
                "askLevels", askLevels.size(),
                "totalOrders", getTotalOrders()
        );
    }

    // ==================== PRIVATE METHODS ====================

    private void addToBidLevel(PlaceOrderCommandDTO order, OrderNode orderNode) {
        BidPriceLevel level = bidLevels.get(order.getPrice());
        if (level == null) {
            level = new BidPriceLevel(order.getPrice());
            bidLevels.put(order.getPrice(), level);
            bidHeap.insert(order.getPrice());
            invalidateBidCache();
        }
        level.addOrder(order, orderNode);
    }

    private void addToAskLevel(PlaceOrderCommandDTO order, OrderNode orderNode) {
        AskPriceLevel level = askLevels.get(order.getPrice());
        if (level == null) {
            level = new AskPriceLevel(order.getPrice());
            askLevels.put(order.getPrice(), level);
            askHeap.insert(order.getPrice());
            invalidateAskCache();
        }
        level.addOrder(order, orderNode);
    }

    private void removeFromBidLevel(PlaceOrderCommandDTO order) {
        BidPriceLevel level = bidLevels.get(order.getPrice());
        if (level == null) return;

        level.removeOrder(order);
        if (level.isEmpty()) {
            bidLevels.remove(order.getPrice());
            bidHeap.remove(order.getPrice());
            invalidateBidCache();
        }
    }

    private void removeFromAskLevel(PlaceOrderCommandDTO order) {
        AskPriceLevel level = askLevels.get(order.getPrice());
        if (level == null) return;

        level.removeOrder(order);
        if (level.isEmpty()) {
            askLevels.remove(order.getPrice());
            askHeap.remove(order.getPrice());
            invalidateAskCache();
        }
    }

    private void invalidateBidCache() {
        bidCacheDirty = true;
    }

    private void invalidateAskCache() {
        askCacheDirty = true;
    }

    // ==================== SUPPORTING CLASSES ====================

    @Getter
    public static class OrderNode {
        private final PlaceOrderCommandDTO order;

        public OrderNode(PlaceOrderCommandDTO order) {
            this.order = order;
        }
    }

    @Getter
    public abstract static class PriceLevel {
        private final BigDecimal price;
        private final LinkedList<PlaceOrderCommandDTO> orders;
        private int totalVolume;
        private LocalDateTime lastUpdated;

        protected PriceLevel(BigDecimal price) {
            this.price = price;
            this.orders = new LinkedList<>();
            this.totalVolume = 0;
            this.lastUpdated = LocalDateTime.now();
        }

        public boolean isEmpty() {
            return orders.isEmpty();
        }

        public void addOrder(PlaceOrderCommandDTO order, OrderNode orderNode) {
            orders.addLast(order);
            totalVolume += order.getAmount();
            lastUpdated = LocalDateTime.now();
        }

        public void removeOrder(PlaceOrderCommandDTO order) {
            orders.removeIf(o -> o.getOrderId().equals(order.getOrderId()));
            totalVolume -= order.getAmount();
            lastUpdated = LocalDateTime.now();
        }

        public void recalculateTotalVolume(Map<String, Integer> remainingAmounts) {
            totalVolume = orders.stream()
                    .mapToInt(o -> remainingAmounts.getOrDefault(o.getOrderId(), o.getAmount()))
                    .sum();
            lastUpdated = LocalDateTime.now();
        }
    }

    public static class BidPriceLevel extends PriceLevel {
        public BidPriceLevel(BigDecimal price) {
            super(price);
        }
    }

    public static class AskPriceLevel extends PriceLevel {
        public AskPriceLevel(BigDecimal price) {
            super(price);
        }
    }
}

package com.example.carbon_credit.Service;

import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Entity.Trade;
import com.example.carbon_credit.Repository.OrderRepository;
import com.example.carbon_credit.Repository.TradeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;  // ← THÊM: Import cho convert Map → Order
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional  // ← THÊM: Rollback consistency cho JPA/Redis
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;  // JPA cho Order

    @Autowired
    private TradeRepository tradeRepository;  // JPA cho Trade

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;  // Redis (Object để linh hoạt Order)

    @Autowired
    private ContractService contractService;

    @Autowired
    private WsService wsService;

    @Autowired
    private ObjectMapper objectMapper;  // ← THÊM: Spring auto-inject cho convert JSON/Map

    public Order placeOrder(Order order) {
        // Gen ID nếu chưa có
        if (order.getId() == null) {
            order.setId(UUID.randomUUID().toString());
        }
        order.setRemainingAmount(order.getAmount());
        order.setStatus("OPEN");
        order.setCreatedAt(LocalDateTime.now());

        // Lưu JPA persistence
        orderRepository.save(order);

        // ← SỬA: Robust key selection với trim + uppercase (tránh lowercase/null)
        String orderTypeUpper = (order.getOrderType() != null ? order.getOrderType().trim().toUpperCase() : "").equals("BUY") ? "BUY" : "SELL";
        String key = orderTypeUpper.equals("BUY") ? "orders:buy" : "orders:sell";
        System.out.println("Placing order ID: " + order.getId() + " type: " + orderTypeUpper + " to Redis key: " + key + " price: " + order.getPrice());  // Debug

        redisTemplate.opsForZSet().add(key, order, order.getPrice().doubleValue());

        // Broadcast order realtime
        wsService.broadcastOrder(order);

        // Trigger matching
        List<Trade> trades = matchOrders(order);
        for (Trade trade : trades) {
            // Tự động settle on-chain (convert Long to BigInteger)
            try {
                // Validate positive (giữ nguyên)
                if (trade.getPrice().compareTo(BigDecimal.ZERO) <= 0 || trade.getAmount() <= 0) {
                    throw new IllegalArgumentException("Invalid trade: price/amount must be positive");
                }

                // Scale price safe (giữ nguyên + log)
                BigDecimal scaledPrice = trade.getPrice().multiply(BigDecimal.valueOf(100));
                scaledPrice = scaledPrice.setScale(0, RoundingMode.DOWN);  // Truncate decimal
                System.out.println("Scaled price: " + scaledPrice);

                // ← FIX: Query buy/sell orders để lấy userId (addresses) thay vì orderId (UUID)
                Optional<Order> buyOpt = orderRepository.findById(trade.getBuyOrderId());
                Optional<Order> sellOpt = orderRepository.findById(trade.getSellOrderId());
                if (buyOpt.isEmpty() || sellOpt.isEmpty()) {
                    throw new IllegalArgumentException("Missing buy/sell order for trade: " + trade.getId());
                }
                Order buyOrder = buyOpt.get();
                Order sellOrder = sellOpt.get();
                String buyerAddress = buyOrder.getUserId();  // Phải là Ethereum address hợp lệ
                String sellerAddress = sellOrder.getUserId();

                // ← VALIDATE: Kiểm tra addresses hợp lệ trước khi gọi contract
                if (buyerAddress == null || !isValidEthereumAddress(buyerAddress)) {
                    throw new IllegalArgumentException("Invalid buyer address: " + buyerAddress + " (must be 0x followed by 40 hex characters)");
                }
                if (sellerAddress == null || !isValidEthereumAddress(sellerAddress)) {
                    throw new IllegalArgumentException("Invalid seller address: " + sellerAddress + " (must be 0x followed by 40 hex characters)");
                }

                // Normalize addresses (đảm bảo lowercase và có 0x prefix)
                buyerAddress = normalizeAddress(buyerAddress);
                sellerAddress = normalizeAddress(sellerAddress);

                System.out.println("Settling trade with buyer: " + buyerAddress + ", seller: " + sellerAddress + ", amount: " + trade.getAmount() + ", price: " + scaledPrice);

                // Gọi contract với addresses (không UUID)
                String txHash = contractService.settleTrade(
                        buyerAddress, sellerAddress,  // ← FIX: Addresses thay orderId
                        BigInteger.valueOf(trade.getAmount().longValue()),
                        scaledPrice.toBigInteger()
                );
                trade.setTxHash(txHash);
                trade.setTradeAt(LocalDateTime.now());

                // Lưu Trade JPA
                tradeRepository.save(trade);

                // Update order status/remaining
                updateOrderAfterTrade(trade);

                // Broadcast trade realtime
                wsService.broadcastTrade(trade);
            } catch (Exception e) {
                // Rollback
                redisTemplate.opsForZSet().remove(key, order);
                System.err.println("Settle failed for trade " + trade.getId() + ": " + e.getMessage());
                throw new RuntimeException("Settle failed: " + e.getMessage(), e);
            }
        }

        // Update order JPA nếu partial filled
        orderRepository.save(order);

        return order;
    }

    private List<Trade> matchOrders(Order newOrder) {
        List<Trade> trades = new ArrayList<>();
        String orderTypeUpper = (newOrder.getOrderType() != null ? newOrder.getOrderType().trim().toUpperCase() : "").equals("BUY") ? "BUY" : "SELL";
        String oppositeKey = orderTypeUpper.equals("BUY") ? "orders:sell" : "orders:buy";
        System.out.println("Matching " + orderTypeUpper + " order ID: " + newOrder.getId() + " price: " + newOrder.getPrice() + " on key: " + oppositeKey);  // Debug

        // ← SỬA: Range query đúng cho matching
        Set<Object> oppositeObjects;
        if (orderTypeUpper.equals("BUY")) {
            // Buy: Match sell orders price ≤ buy.price (cheapest sell first, asc score)
            oppositeObjects = redisTemplate.opsForZSet().rangeByScore(oppositeKey, 0, newOrder.getPrice().doubleValue());
        } else {
            // Sell: Match buy orders price ≥ sell.price (highest buy first, desc score)
            oppositeObjects = redisTemplate.opsForZSet().reverseRangeByScore(oppositeKey, newOrder.getPrice().doubleValue(), Double.MAX_VALUE);
        }

        System.out.println("Opposite ZSet raw size: " + oppositeObjects.size());  // ← THÊM: Debug raw retrieve

        List<Order> oppositeOrders = new ArrayList<>();
        for (Object obj : oppositeObjects) {
            Order oppOrder;
            if (obj instanceof Order) {
                oppOrder = (Order) obj;
            } else if (obj instanceof Map) {  // ← SỬA: Handle JSON deserialized as Map (tương tự snapshot)
                try {
                    oppOrder = objectMapper.convertValue(obj, Order.class);  // Convert Map → Order
                    System.out.println("Converted Map to oppOrder ID: " + oppOrder.getId());  // ← THÊM: Confirm convert
                } catch (Exception e) {
                    System.err.println("Failed to convert oppOrder Map to Order: " + e.getMessage());
                    continue;
                }
            } else {
                System.err.println("Unexpected oppOrder type: " + (obj != null ? obj.getClass().getName() : "null"));  // ← THÊM: Log type
                continue;
            }
            // ← SỬA: Check remaining amount từ Hash (nếu partial)
            Integer remaining = (Integer) redisTemplate.opsForHash().get("order:" + oppOrder.getId(), "remaining");
            if (remaining != null && remaining <= 0) continue;  // Skip filled
            oppositeOrders.add(oppOrder);
        }

        System.out.println("Found " + oppositeOrders.size() + " opposite orders for matching (after filter)");  // Debug

        for (Order oppOrder : oppositeOrders) {
            System.out.println("Checking match with oppOrder ID: " + oppOrder.getId() + " price: " + oppOrder.getPrice() + " creditId: " + oppOrder.getCreditId());  // ← THÊM: Log each opp

            if (!newOrder.getCreditId().equals(oppOrder.getCreditId())) {
                System.out.println("Skipped: Different creditId (" + newOrder.getCreditId() + " vs " + oppOrder.getCreditId() + ")");  // ← THÊM: Log skip
                continue;
            }

            // ← SỬA: Check price cross
            boolean priceCross = (orderTypeUpper.equals("BUY") && newOrder.getPrice().compareTo(oppOrder.getPrice()) >= 0) ||
                    (orderTypeUpper.equals("SELL") && newOrder.getPrice().compareTo(oppOrder.getPrice()) <= 0);
            if (!priceCross) {
                System.out.println("Skipped: No price cross (" + newOrder.getPrice() + " vs " + oppOrder.getPrice() + ")");  // ← THÊM: Log skip
                continue;
            }

            // Get remaining (fallback to full amount nếu Hash null)
            Integer newRemaining = (Integer) redisTemplate.opsForHash().get("order:" + newOrder.getId(), "remaining");
            newRemaining = newRemaining != null ? newRemaining : newOrder.getRemainingAmount().intValue();  // ← SỬA: .intValue() safe
            Integer oppRemaining = (Integer) redisTemplate.opsForHash().get("order:" + oppOrder.getId(), "remaining");
            oppRemaining = oppRemaining != null ? oppRemaining : oppOrder.getRemainingAmount().intValue();

            int matchAmount = Math.min(newRemaining, oppRemaining);
            if (matchAmount <= 0) {
                System.out.println("Skipped: No remaining amount (new: " + newRemaining + ", opp: " + oppRemaining + ")");  // ← THÊM: Log skip
                break;
            }

            // Tạo Trade object
            Trade trade = Trade.builder()
                    .id(UUID.randomUUID().toString())
                    .buyOrderId(orderTypeUpper.equals("BUY") ? newOrder.getId() : oppOrder.getId())
                    .sellOrderId(orderTypeUpper.equals("BUY") ? oppOrder.getId() : newOrder.getId())
                    .creditId(newOrder.getCreditId())
                    .price(newOrder.getPrice())  // Use newOrder price for trade (market price)
                    .amount(matchAmount)
                    .totalValue(newOrder.getPrice().multiply(BigDecimal.valueOf(matchAmount)))
                    .build();

            trades.add(trade);
            System.out.println("Created trade: " + trade.getId() + " amount: " + matchAmount + " price: " + trade.getPrice());  // Debug

            // Update remaining in Hash
            newOrder.setRemainingAmount(newRemaining - matchAmount);
            oppOrder.setRemainingAmount(oppRemaining - matchAmount);

            redisTemplate.opsForHash().put("order:" + newOrder.getId(), "remaining", newOrder.getRemainingAmount());
            redisTemplate.opsForHash().put("order:" + oppOrder.getId(), "remaining", oppOrder.getRemainingAmount());

            // ← SỬA: Remove trước add để tránh duplicate in ZSet
            String newKey = orderTypeUpper.equals("BUY") ? "orders:buy" : "orders:sell";
            redisTemplate.opsForZSet().remove(newKey, newOrder);  // Remove cũ trước
            redisTemplate.opsForZSet().add(newKey, newOrder, newOrder.getPrice().doubleValue());  // Add updated

            String oppKey = (oppOrder.getOrderType() != null ? oppOrder.getOrderType().trim().toUpperCase() : "").equals("BUY") ? "orders:buy" : "orders:sell";
            redisTemplate.opsForZSet().remove(oppKey, oppOrder);  // Remove cũ
            redisTemplate.opsForZSet().add(oppKey, oppOrder, oppOrder.getPrice().doubleValue());  // Add updated

            // ← SỬA: Remove nếu filled (remaining ≤ 0)
            if (newOrder.getRemainingAmount() <= 0) {
                redisTemplate.opsForZSet().remove(newKey, newOrder);
                wsService.broadcastOrderUpdate(newOrder);  // Broadcast filled
            }
            if (oppOrder.getRemainingAmount() <= 0) {
                redisTemplate.opsForZSet().remove(oppKey, oppOrder);
                wsService.broadcastOrderUpdate(oppOrder);  // Broadcast filled
            }

            if (newOrder.getRemainingAmount() <= 0 || oppOrder.getRemainingAmount() <= 0) break;
        }

        System.out.println("Matching completed, created " + trades.size() + " trades");  // Debug
        return trades;
    }

    private void updateOrderAfterTrade(Trade trade) {
        // Update buy order
        Optional<Order> buyOpt = orderRepository.findById(trade.getBuyOrderId());
        if (buyOpt.isPresent()) {
            Order buyOrder = buyOpt.get();
            buyOrder.setRemainingAmount(buyOrder.getAmount() - trade.getAmount());
            if (buyOrder.getRemainingAmount() <= 0) {
                buyOrder.setStatus("FILLED");
            } else {
                buyOrder.setStatus("PARTIAL_FILLED");
            }
            orderRepository.save(buyOrder);
            wsService.broadcastOrderUpdate(buyOrder);  // Broadcast update status
        }

        // Update sell order
        Optional<Order> sellOpt = orderRepository.findById(trade.getSellOrderId());
        if (sellOpt.isPresent()) {
            Order sellOrder = sellOpt.get();
            sellOrder.setRemainingAmount(sellOrder.getAmount() - trade.getAmount());
            if (sellOrder.getRemainingAmount() <= 0) {
                sellOrder.setStatus("FILLED");
            } else {
                sellOrder.setStatus("PARTIAL_FILLED");
            }
            orderRepository.save(sellOrder);
            wsService.broadcastOrderUpdate(sellOrder);  // Broadcast update status
        }
    }

    public List<Order> getMyOrders(String userId) {
        // SỬA: Dùng method mới
        return orderRepository.findByUserIdAndStatus(userId, "OPEN");
    }

    // ← THÊM: Get full snapshot từ Redis (all OPEN orders, filter status in query nếu cần)
    public Map<String, Object> getSnapshot() {
        Set<Object> buyObjects = redisTemplate.opsForZSet().reverseRange("orders:buy", 0, -1);  // Desc price cho buy
        Set<Object> sellObjects = redisTemplate.opsForZSet().range("orders:sell", 0, -1);  // Asc price cho sell

        System.out.println("Redis buy ZSet size: " + buyObjects.size() + ", sell ZSet size: " + sellObjects.size());  // ← THÊM: Debug size trước filter

        List<Order> buyOrders = new ArrayList<>();
        for (Object obj : buyObjects) {
            Order o;
            if (obj instanceof Order) {
                o = (Order) obj;
            } else if (obj instanceof Map) {  // ← SỬA: Handle JSON deserialized as Map (từ Redis JSON serializer)
                try {
                    o = objectMapper.convertValue(obj, Order.class);  // Convert Map → Order
                } catch (Exception e) {
                    System.err.println("Failed to convert buy order Map to Order: " + e.getMessage());
                    continue;
                }
            } else {
                System.err.println("Unexpected buy object type: " + (obj != null ? obj.getClass().getName() : "null"));  // ← THÊM: Log type để debug
                continue;
            }
            // Filter OPEN only (skip filled)
            if ("OPEN".equals(o.getStatus()) || "PARTIAL_FILLED".equals(o.getStatus())) {
                buyOrders.add(o);
            }
        }

        List<Order> sellOrders = new ArrayList<>();
        for (Object obj : sellObjects) {
            Order o;
            if (obj instanceof Order) {
                o = (Order) obj;
            } else if (obj instanceof Map) {  // ← SỬA: Handle JSON as Map
                try {
                    o = objectMapper.convertValue(obj, Order.class);  // Convert Map → Order
                } catch (Exception e) {
                    System.err.println("Failed to convert sell order Map to Order: " + e.getMessage());
                    continue;
                }
            } else {
                System.err.println("Unexpected sell object type: " + (obj != null ? obj.getClass().getName() : "null"));  // ← THÊM: Log type để debug
                continue;
            }
            if ("OPEN".equals(o.getStatus()) || "PARTIAL_FILLED".equals(o.getStatus())) {
                sellOrders.add(o);
            }
        }

        System.out.println("Snapshot after filter: " + buyOrders.size() + " buy, " + sellOrders.size() + " sell");  // ← THÊM: Debug after filter
        return Map.of("type", "snapshot", "orders", List.of(buyOrders, sellOrders));
    }

    /**
     * Kiểm tra xem string có phải là Ethereum address hợp lệ không
     * Address hợp lệ: bắt đầu bằng 0x và có 42 ký tự tổng cộng (0x + 40 hex chars)
     */
    private boolean isValidEthereumAddress(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        // Loại bỏ khoảng trắng
        address = address.trim();
        // Kiểm tra format: 0x + 40 hex characters
        if (!address.startsWith("0x") && !address.startsWith("0X")) {
            return false;
        }
        String hexPart = address.substring(2);
        if (hexPart.length() != 40) {
            return false;
        }
        // Kiểm tra tất cả ký tự là hex (0-9, a-f, A-F)
        return hexPart.matches("[0-9a-fA-F]{40}");
    }

    /**
     * Normalize Ethereum address: lowercase và đảm bảo có 0x prefix
     */
    private String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        address = address.trim();
        // Nếu không có 0x prefix, thêm vào
        if (!address.startsWith("0x") && !address.startsWith("0X")) {
            address = "0x" + address;
        }
        // Convert to lowercase
        return address.toLowerCase();
    }
}
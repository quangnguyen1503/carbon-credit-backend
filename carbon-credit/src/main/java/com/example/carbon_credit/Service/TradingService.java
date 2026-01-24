package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.PlaceOrderCommandDTO;
import com.example.carbon_credit.DTO.TradeEventDTO;
import com.example.carbon_credit.Entity.CarbonCredit;
import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Kafka.KafkaProducerService;
import com.example.carbon_credit.MatchingEngine.MatchingEngine;
import com.example.carbon_credit.Repository.CarbonCreditRepository;
import com.example.carbon_credit.Repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingService {

    private final OrderRepository orderRepository;
    private final CarbonCreditRepository carbonCreditRepository;
    private final KafkaProducerService kafkaProducerService;
    private final MatchingEngine matchingEngine;
    private final ContractService contractService;
    private final WsService wsService;
    @Lazy
    private final SettlementService settlementService;

    private static final BigDecimal SLIPPAGE_BUFFER = new BigDecimal("1.05");

    /**
     * Place order: Lưu DB + Gửi vào Kafka
     */
    @Transactional
    public Order placeOrder(PlaceOrderCommandDTO request, String userId) {
        // Validation
        if (request.getAmount() <= 0 || request.getPrice().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid amount or price");
        }

        boolean isMarket = "MARKET".equalsIgnoreCase(request.getOrderCondition());
        boolean isBuy = "BUY".equalsIgnoreCase(request.getOrderType());

        BigDecimal calculationPrice;

        if (isMarket) {
            if (isBuy) {
                Map<String, Object> snapshot = matchingEngine.getOrderBookSnapshot(request.getCreditId());
                BigDecimal bestAsk = (BigDecimal) snapshot.get("bestAsk");

                if (bestAsk == null || bestAsk.compareTo(BigDecimal.ZERO) == 0) {
                    throw new RuntimeException("Cannot place Market Buy Order: No sellers available (No Liquidity)");
                }

                calculationPrice = bestAsk.multiply(SLIPPAGE_BUFFER);

                request.setPrice(calculationPrice);
            } else {
                calculationPrice = BigDecimal.ZERO;
                request.setPrice(calculationPrice);
            }
        } else {
            if (request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Limit Order requires price > 0");
            }
            calculationPrice = request.getPrice();
        }

        BigInteger amount = BigInteger.valueOf(request.getAmount());
        BigInteger priceWei = calculationPrice.multiply(new BigDecimal("1000000000000000000")).toBigInteger();
        BigInteger totalValue = amount.multiply(priceWei);

        try {
            if (request.getOrderType().equalsIgnoreCase("BUY")) {
                BigInteger nativeBalance = contractService.getNativeBalance(userId);
                if (nativeBalance.compareTo(totalValue) < 0) {
                    String.format("Insufficient native balance. Required: %s, Available: %s", totalValue,
                            nativeBalance);
                }
            } else if (request.getOrderType().equalsIgnoreCase("SELL")) {
                BigInteger creditTokenId = new BigInteger(request.getCreditId());
                BigInteger creditBalance = contractService.getCreditBalance(userId, creditTokenId);
                if (creditBalance.compareTo(amount) < 0) {
                    throw new IllegalArgumentException(
                            String.format("Insufficient credit balance. Required: %d, Available: %s",
                                    request.getAmount(), creditBalance));
                }
            }
        } catch (Exception e) {
            log.error("Fail to query balance from smart contract", e.getMessage());
            throw new RuntimeException("Fail to verify balance on blockchain", e);
        }

        // Tạo order entity
        String orderId = UUID.randomUUID().toString();
        Order order = Order.builder()
                .id(orderId)
                .userId(userId)
                .creditId(request.getCreditId())
                .orderType(request.getOrderType())
                .orderCondition(request.getOrderCondition())
                .price(request.getPrice())
                .amount(request.getAmount())
                .remainingAmount(request.getAmount())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Lưu DB trước
        orderRepository.save(order);

        // Khoá số dư on-chain
        try {
            BigInteger creditTokenId = new BigInteger(request.getCreditId());

            boolean isCreditToken = request.getOrderType().equalsIgnoreCase("SELL");

            BigInteger lockAmount = isCreditToken ? amount : totalValue;

            contractService.lockBalance(orderId, userId.trim().toLowerCase(), creditTokenId, lockAmount, isCreditToken);

            order.setStatus("OPEN");
            orderRepository.save(order);

        } catch (Exception e) {
            order.setStatus("FAILED");
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            throw new RuntimeException("Failed to lock balance on blockchain", e);

        }

        // Tạo command để gửi Kafka
        PlaceOrderCommandDTO command = PlaceOrderCommandDTO.builder()
                .orderId(order.getId())
                .userId(userId)
                .creditId(request.getCreditId())
                .orderType(request.getOrderType())
                .orderCondition(request.getOrderCondition())
                .price(isMarket ? BigDecimal.ZERO : request.getPrice())
                .amount(request.getAmount())
                .build();

        // Gửi vào Kafka (bất đồng bộ)
        kafkaProducerService.sendOrder(command);

        // Giả sử wsService.notify(title, message, type, role, wallet)
        wsService.notify(
                String.format("Đặt %s thành công", order.getOrderType()),
                String.format("Đặt %s với giá: %s và số lượng %s.",
                        order.getOrderType(),
                        order.getPrice(),
                        order.getAmount()),
                "SUCCESS",
                null,
                userId.toLowerCase());

        log.info(" Order {} sent to matching engine", order.getId());
        return order;
    }

    /**
     * Cancel order: Remove từ matching engine + Remove from settlement batch +
     * Update DB
     */
    @Transactional
    public boolean cancelOrder(Order order) {
        String orderId = order.getId();

        boolean removedFromBook = matchingEngine.cancelOrder(order.getCreditId(), orderId);

        List<TradeEventDTO> removedTrades = settlementService.removeTradesForOrder(orderId);
        boolean removedFromBatch = !removedTrades.isEmpty();

        if (!removedFromBook && !removedFromBatch) {
            log.warn(" Order {} not found in OrderBook or BatchQueue - cannot cancel", orderId);
            wsService.notify(
                    "Hủy lệnh thất bại",
                    String.format("Không thể hủy lệnh %s. Lệnh đã được xử lý hoặc không tồn tại.", orderId),
                    "ERROR",
                    null,
                    order.getUserId().toLowerCase());
            return false;
        }

        // Nếu có trades bị xóa từ batch, cần unlock counterparty orders
        if (removedFromBatch) {
            for (TradeEventDTO trade : removedTrades) {
                String counterpartyOrderId = trade.getBuyOrderId().equals(orderId)
                        ? trade.getSellOrderId()
                        : trade.getBuyOrderId();

                try {
                    // Unlock counterparty order on-chain
                    contractService.unlockBalance(counterpartyOrderId);
                    log.info(" Unlocked counterparty order {} due to cancellation of {}", counterpartyOrderId,
                            orderId);

                    // Restore counterparty order to OPEN status and add back to orderbook
                    orderRepository.findById(counterpartyOrderId).ifPresent(counterpartyOrder -> {
                        // Restore remaining amount that was matched
                        counterpartyOrder
                                .setRemainingAmount(counterpartyOrder.getRemainingAmount() + trade.getAmount());
                        counterpartyOrder.setStatus("OPEN");
                        counterpartyOrder.setUpdatedAt(LocalDateTime.now());
                        orderRepository.save(counterpartyOrder);

                        // Re-add to OrderBook for matching
                        PlaceOrderCommandDTO restoreCommand = PlaceOrderCommandDTO.builder()
                                .orderId(counterpartyOrder.getId())
                                .userId(counterpartyOrder.getUserId())
                                .creditId(counterpartyOrder.getCreditId())
                                .orderType(counterpartyOrder.getOrderType())
                                .orderCondition(counterpartyOrder.getOrderCondition())
                                .price(counterpartyOrder.getPrice())
                                .amount(counterpartyOrder.getRemainingAmount())
                                .build();

                        // Process order - sẽ match nếu có order đối ứng, hoặc thêm vào book
                        List<TradeEventDTO> newTrades = matchingEngine.processOrder(restoreCommand);
                        if (!newTrades.isEmpty()) {
                            // Nếu có trades mới, gửi vào settlement
                            for (TradeEventDTO newTrade : newTrades) {
                                settlementService.addTradeToBatch(newTrade);
                            }
                            log.info("Restored order {} matched {} new trades", counterpartyOrderId, newTrades.size());
                        } else {
                            log.info("Restored order {} added to OrderBook", counterpartyOrderId);
                        }

                        // Notify counterparty
                        wsService.notify(
                                "Giao dịch bị hủy",
                                String.format("Lệnh của bạn được khôi phục do đối tác hủy giao dịch."),
                                "WARNING",
                                null,
                                counterpartyOrder.getUserId().toLowerCase());
                    });
                } catch (Exception e) {
                    log.error(" Failed to unlock counterparty order {}: {}", counterpartyOrderId, e.getMessage());
                }
            }
        }

        // 4. Unlock balance for cancelled order
        try {
            contractService.unlockBalance(orderId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to unlock balance on blockchain", e);
        }

        // 5. Update order status
        order.setStatus("CANCELLED");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // 6. Broadcast updates
        Map<String, Object> snapshot = matchingEngine.getOrderBookSnapshot(order.getCreditId());
        wsService.broadcastOrderBookUpdate(order.getCreditId(), snapshot);
        wsService.notifyOrderCancelled(order.getUserId(), order.getId(), order.getCreditId());

        log.info("Order {} cancelled successfully (fromBook={}, fromBatch={})",
                orderId, removedFromBook, removedFromBatch);

        wsService.notify(
                String.format("Hủy %s thành công", order.getOrderType()),
                String.format("Bạn đã hủy %s với giá: %s và số lượng %s.",
                        order.getOrderType(),
                        order.getPrice(),
                        order.getAmount()),
                "SUCCESS",
                null,
                order.getUserId().toLowerCase());

        return true;
    }

    /**
     * Expire order: Remove from matching engine + Unlock Balance + Update DB
     * (EXPIRED)
     * Called by scheduled task.
     */
    @Transactional
    public void expireOrder(Order order) {
        boolean removed = matchingEngine.cancelOrder(order.getCreditId(), order.getId());

        if (removed) {
            log.info("removed order from matching engine: {}", order.getId());
            // Broadcast OrderBook Update immediately
            Map<String, Object> snapshot = matchingEngine.getOrderBookSnapshot(order.getCreditId());
            wsService.broadcastOrderBookUpdate(order.getCreditId(), snapshot);
        } else {
            log.warn("Order {} not found in Matching Engine during expiry", order.getId());
        }

        try {
            contractService.unlockBalance(order.getId());
            log.info("Unlocked balance on blockchain for expired order {}", order.getId());
        } catch (Exception e) {
            log.error("Failed to unlock balance for expired order {}: {}", order.getId(), e.getMessage());
        }

        order.setStatus("EXPIRED");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        wsService.notifyOrderExpired(order.getUserId(), order.getId(), order.getCreditId());

        log.info(" Order {} expired successfully", order.getId());
    }

    public boolean hasActiveOrderBook(String projectId) {
        CarbonCredit credit = carbonCreditRepository.findByProjectId(projectId).orElse(null);
        if (credit == null)
            return false;

        String creditId = String.valueOf(credit.getTokenId());
        return matchingEngine.hasOrderBook(creditId);
    }

    public void ensureOrderBookExists(String creditId) {
        if (!matchingEngine.hasOrderBook(creditId)) {
            log.info("📊 Creating OrderBook for first order: creditId={}", creditId);
            matchingEngine.createOrderBook(creditId);
        }
    }

    public Map<String, Object> getOrderBookSnapshot(String creditId) {
        return matchingEngine.getOrderBookSnapshot(creditId);
    }

}
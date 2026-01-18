package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.PlaceOrderCommandDTO;
import com.example.carbon_credit.Entity.CarbonCredit;
import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Kafka.KafkaProducerService;
import com.example.carbon_credit.MatchingEngine.MatchingEngine;
import com.example.carbon_credit.Repository.CarbonCreditRepository;
import com.example.carbon_credit.Repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
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

    /**
     * Place order: Lưu DB + Gửi vào Kafka
     */
    @Transactional
    public Order placeOrder(PlaceOrderCommandDTO request, String userId) {
        // Validation
        if (request.getAmount() <= 0 || request.getPrice().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid amount or price");
        }

        BigInteger amount = BigInteger.valueOf(request.getAmount());
        BigInteger priceWei = request.getPrice().multiply(new BigDecimal("1000000000000000000")).toBigInteger();
        BigInteger totalValue = amount.multiply(priceWei);

        try {
            if (request.getOrderType().equalsIgnoreCase("BUY")) {
                BigInteger nativeBalance = contractService.getNativeBalance(userId);
                if (nativeBalance.compareTo(totalValue) < 0) {
                    String.format("Insufficient native balance. Required: %s, Available: %s", totalValue, nativeBalance);
                }
            } else if (request.getOrderType().equalsIgnoreCase("SELL")) {
                BigInteger creditTokenId = new BigInteger(request.getCreditId());
                BigInteger creditBalance = contractService.getCreditBalance(userId, creditTokenId);
                if (creditBalance.compareTo(amount) < 0) {
                    throw new IllegalArgumentException(String.format("Insufficient credit balance. Required: %d, Available: %s", request.getAmount(), creditBalance));
                }
            }
        } catch (Exception e) {
            log.error("Fail to query balance from smart contract", e.getMessage());
            throw new RuntimeException("Fail to verify balance on blockchain", e);
        }

        // Tạo order entity
        String orderId = UUID.randomUUID().toString();
        Order order = Order.builder().id(orderId).userId(userId).creditId(request.getCreditId()).orderType(request.getOrderType()).orderCondition(request.getOrderCondition()).price(request.getPrice()).amount(request.getAmount()).remainingAmount(request.getAmount()).status("PENDING").createdAt(LocalDateTime.now()).build();

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
        PlaceOrderCommandDTO command = PlaceOrderCommandDTO.builder().orderId(order.getId()).userId(userId).creditId(request.getCreditId()).orderType(request.getOrderType()).orderCondition(request.getOrderCondition()).price(request.getPrice()).amount(request.getAmount()).build();

        // Gửi vào Kafka (bất đồng bộ)
        kafkaProducerService.sendOrder(command);

        // Giả sử wsService.notify(title, message, type, role, wallet)
        wsService.notify(
                String.format("Đặt %s thành công", order.getOrderType()),  // Title: "Đặt BUY thành công" (hoặc SELL)
                String.format("Đặt %s với giá: %s và số lượng %s.",
                        order.getOrderType(),
                        order.getPrice(),
                        order.getAmount()),  // Message: "Đặt BUY với giá: 1000 và số lượng 5."
                "SUCCESS",  // Type
                null,       // Role (null nếu gửi cá nhân)
                request.getUserId()  // Wallet/Recipient
        );

        log.info("📤 Order {} sent to matching engine", order.getId());
        return order;
    }

    /**
     * Cancel order: Remove từ matching engine + Update DB
     */
    @Transactional
    public boolean cancelOrder(Order order) {
        // Cancel trong matching engine
        boolean removed = matchingEngine.cancelOrder(order.getCreditId(), order.getId());

        if (removed) {
            try {
                contractService.unlockBalance(order.getId());
            } catch (Exception e) {
                throw new RuntimeException("Failed to unlock balance on blockchain", e);
            }

            order.setStatus("CANCELLED");
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            log.info("✅ Order {} cancelled", order.getId());
            return true;
        }

        log.warn("❌ Failed to cancel order {}", order.getId());
        wsService.notify(
                String.format("Hủy %s thành công", order.getOrderType()),  // Title: "Hủy BUY thành công" (hoặc SELL)
                String.format("Bạn đã hủy %s với giá: %s và số lượng %s.",
                        order.getOrderType(),
                        order.getPrice(),
                        order.getAmount()),  // Message: "Bạn đã hủy BUY với giá: 1000 và số lượng 5."
                "WARNING",  // Type: WARNING để phân biệt (có thể dùng INFO nếu nhẹ hơn)
                null,       // Role (null nếu gửi cá nhân)
                order.getUserId()      // Wallet/Recipient (từ request hoặc order.getUserId())
        );
        return false;
    }

    public boolean hasActiveOrderBook(String projectId) {
        CarbonCredit credit = carbonCreditRepository.findByProjectId(projectId).orElse(null);
        if (credit == null) return false;

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
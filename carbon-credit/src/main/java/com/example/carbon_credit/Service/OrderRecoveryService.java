package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.PlaceOrderCommandDTO;
import com.example.carbon_credit.DTO.TradeEventDTO;
import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.MatchingEngine.MatchingEngine;
import com.example.carbon_credit.Repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderRecoveryService implements CommandLineRunner {

    private final OrderRepository orderRepository;
    private final MatchingEngine matchingEngine;
    private final SettlementService settlementService;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting Order Recovery...");

        // 1. Fetch all OPEN orders
        List<Order> openOrders = orderRepository.findByStatusOrderByCreatedAtAsc("OPEN");

        if (openOrders.isEmpty()) {
            log.info("No open orders found to recover.");
            return;
        }

        log.info("Recovering {} open orders...", openOrders.size());

        for (Order order : openOrders) {
            try {
                restoreOrder(order);
            } catch (Exception e) {
                log.error("Failed to recover order {}: {}", order.getId(), e.getMessage());
            }
        }

        log.info("Order Recovery Completed.");
    }

    private void restoreOrder(Order order) {
        // Skip if remaining amount is 0
        if (order.getRemainingAmount() <= 0) {
            log.warn("Order {} has status OPEN but remaining amount is 0. Skipping.", order.getId());
            return;
        }

        // Convert to DTO
        PlaceOrderCommandDTO command = PlaceOrderCommandDTO.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .creditId(order.getCreditId())
                .orderType(order.getOrderType())
                .orderCondition(order.getOrderCondition())
                .price(order.getPrice())
                .amount(order.getRemainingAmount())
                .build();

        // Feed back into Matching Engine
        // IMPORTANT: If recovery triggers trades (due to race condition or missed
        // settlement), process them!
        List<TradeEventDTO> trades = matchingEngine.processOrder(command);

        if (!trades.isEmpty()) {
            log.warn("Recovery triggered {} trades! Sending to settlement...", trades.size());
            trades.forEach(settlementService::addTradeToBatch);
        }
    }
}

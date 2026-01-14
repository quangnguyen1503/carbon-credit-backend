package com.example.carbon_credit.Service;

import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.MatchingEngine.MatchingEngine;
import com.example.carbon_credit.Repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MatchingEngine matchingEngine;
    private final WsService wsService;  // ← Thêm

    /**
     * Get all orders for a user
     */
    public List<Order> getOrdersByUserId(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Get open orders only
     */
    public List<Order> getOpenOrdersByUserId(String userId) {
        return orderRepository.findByUserIdAndStatus(userId, "OPEN");
    }

    public List<Order> getOrdersByUserIdAndStatus(String userId, String status) {
        return orderRepository.findByUserIdAndStatus(userId, status);
    }

    public List<Order> getOrdersByUserIdAndCreditId(String userId, String creditId) {
        return orderRepository.findByUserIdAndCreditId(userId, creditId);
    }

    public List<Order> getOrdersByUserIdAndStatusAndCreditId(String userId, String status, String creditId) {
        return orderRepository.findByUserIdAndStatusAndCreditId(userId, status, creditId);
    }

    /**
     * Find order by ID
     */
    public Order findById(String orderId) {
        return orderRepository.findById(orderId).orElse(null);
    }

    /**
     * Get orderbook snapshot for a credit
     */
    public Map<String, Object> getOrderBookSnapshot(String creditId) {
        return matchingEngine.getOrderBook(creditId);
    }

    /**
     * Get all orderbooks snapshots
     */
    public Map<String, Map<String, Object>> getAllOrderBookSnapshots() {
        return matchingEngine.getAllOrderBooks();
    }
}
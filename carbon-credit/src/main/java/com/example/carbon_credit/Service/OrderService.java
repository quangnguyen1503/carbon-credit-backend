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
    public Map<String, Object> getSnapshot() {
        // Lấy danh sách lệnh Mua (Sắp xếp giá cao nhất lên đầu)
        List<Order> buyOrders = orderRepository.findByOrderTypeAndStatusOrderByPriceDesc("BUY", "OPEN");

        // Lấy danh sách lệnh Bán (Sắp xếp giá thấp nhất lên đầu)
        List<Order> sellOrders = orderRepository.findByOrderTypeAndStatusOrderByPriceAsc("SELL", "OPEN");

        // Trả về Map chứa List của 2 List (Đúng cấu trúc Controller đang chờ)
        return Map.of("orders", List.of(buyOrders, sellOrders));
    }
}
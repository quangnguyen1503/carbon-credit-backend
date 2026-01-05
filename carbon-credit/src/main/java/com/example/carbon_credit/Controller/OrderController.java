package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.PlaceOrderCommandDTO;
import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Service.OrderService;
import com.example.carbon_credit.Service.TradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OrderController {

    private final TradingService tradingService;
    private final OrderService orderService;

    /**
     * Place order → Send to Kafka → Matching engine xử lý bất đồng bộ
     */
    @PostMapping("/place")
    public ResponseEntity<Order> placeOrder(
            @RequestBody PlaceOrderCommandDTO request,
            Authentication authentication
    ) {
        String userId = authentication.getName();  // Lấy từ JWT

        // TradingService sẽ:
        // 1. Validate order
        // 2. Lưu vào DB với status OPEN
        // 3. Gửi vào Kafka topic "orders"
        Order placedOrder = tradingService.placeOrder(request, userId);

        return ResponseEntity.ok(placedOrder);
    }

    /**
     * Get user's orders (history + open orders)
     */
    @GetMapping("/my-orders")
    public ResponseEntity<List<Order>> getMyOrders(Authentication authentication) {
        String userId = authentication.getName();
        List<Order> orders = orderService.getOrdersByUserId(userId);
        return ResponseEntity.ok(orders);
    }

    /**
     * Get user's open orders only
     */
    @GetMapping("/my-orders/open")
    public ResponseEntity<List<Order>> getMyOpenOrders(Authentication authentication) {
        String userId = authentication.getName();
        List<Order> orders = orderService.getOpenOrdersByUserId(userId);
        return ResponseEntity.ok(orders);
    }

    /**
     * Cancel order
     */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<String> cancelOrder(
            @PathVariable String orderId,
            Authentication authentication
    ) {
        String userId = authentication.getName();

        // Validate ownership
        Order order = orderService.findById(orderId);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        if (!order.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body("Not authorized to cancel this order");
        }

        // Cancel trong matching engine + update DB
        boolean cancelled = tradingService.cancelOrder(order);

        if (cancelled) {
            return ResponseEntity.ok("Order cancelled successfully");
        }

        return ResponseEntity.badRequest().body("Failed to cancel order");
    }

    /**
     * Get order by ID
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrderById(
            @PathVariable String orderId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        Order order = orderService.findById(orderId);

        if (order == null) {
            return ResponseEntity.notFound().build();
        }

        // Validate ownership
        if (!order.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(order);
    }

    /**
     * Get public orderbook snapshot (không cần auth)
     */
    @GetMapping("/snapshot/{creditId}")
    public ResponseEntity<Map<String, Object>> getSnapshot(@PathVariable String creditId) {
        Map<String, Object> snapshot = orderService.getOrderBookSnapshot(creditId);

        if (snapshot == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(snapshot);
    }

    /**
     * Get all orderbooks snapshot
     */
    @GetMapping("/snapshots")
    public ResponseEntity<Map<String, Map<String, Object>>> getAllSnapshots() {
        Map<String, Map<String, Object>> snapshots = orderService.getAllOrderBookSnapshots();
        return ResponseEntity.ok(snapshots);
    }
}
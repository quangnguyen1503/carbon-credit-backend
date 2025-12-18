package com.example.carbon_credit.Controller;

import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/place")
    public ResponseEntity<Order> placeOrder(@RequestBody Order order, Principal principal) {
        order.setUserId(principal.getName());  // Từ JWT
        order.setStatus("OPEN");
        order.setCreatedAt(LocalDateTime.now());
        // Place + match + settle tự động
        Order placedOrder = orderService.placeOrder(order);
        return ResponseEntity.ok(placedOrder);
    }

    @GetMapping("/my-orders")
    public ResponseEntity<List<Order>> getMyOrders(Principal principal) {
        return ResponseEntity.ok(orderService.getMyOrders(principal.getName()));
    }

    // ← THÊM: Fallback HTTP snapshot cho FE (không cần auth nếu public orderbook)
    @GetMapping("/snapshot")
    public ResponseEntity<Map<String, Object>> getSnapshot() {
        Map<String, Object> snapshot = orderService.getSnapshot();
        return ResponseEntity.ok(snapshot);
    }
}
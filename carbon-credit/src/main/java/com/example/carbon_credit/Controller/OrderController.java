package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.PlaceOrderCommandDTO;
import com.example.carbon_credit.DTO.ProjectWithCreditDTO;
import com.example.carbon_credit.Entity.CarbonCredit;
import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Entity.Project;
import com.example.carbon_credit.Service.CarbonCreditService;
import com.example.carbon_credit.Service.OrderService;
import com.example.carbon_credit.Service.ProjectService;
import com.example.carbon_credit.Service.TradingService;
import com.example.carbon_credit.constants.ProjectStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    @Autowired
    private final TradingService tradingService;

    @Autowired
    private final OrderService orderService;

    @Autowired
    private final CarbonCreditService carbonCreditService;

    @Autowired
    private final ProjectService projectService;

    /**
     * Place order → Send to Kafka → Matching engine xử lý bất đồng bộ
     */
    @PostMapping("/place")
    public ResponseEntity<?> placeOrder(
            @Valid @RequestBody PlaceOrderCommandDTO request,
            Authentication authentication
    ) {
        try {
            String userId = authentication.getName();

            log.info(" Placing order: userId={}, type={}, creditId={}, price={}, amount={}",
                    userId, request.getOrderType(), request.getCreditId(),
                    request.getPrice(), request.getAmount());

            // Validate credit exists
            CarbonCredit credit = carbonCreditService.getCarbonCreditByTokenId(
                    Long.parseLong(request.getCreditId())
            );

            if (credit == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "Not Found",
                        "message", "Carbon credit not found"
                ));
            }

            // Check if project is APPROVED
            Project project = projectService.getProjectById(credit.getProjectId());
            if (project == null || !ProjectStatus.APPROVED.equals(project.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Bad Request",
                        "message", "Project must be approved by government before trading"
                ));
            }

            // Additional validation for SELL orders
            if ("SELL".equalsIgnoreCase(request.getOrderType())) {
                // Check if user owns the project (simplified ownership check)
                if (!project.getOwnerId().equals(userId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                            "error", "Forbidden",
                            "message", "You don't own this carbon credit"
                    ));
                }

                // Check available credits
                long availableCredits = credit.getIssueAmount() - credit.getRetiredAmount();
                if (request.getAmount() > availableCredits) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Bad Request",
                            "message", "Insufficient credits. Available: " + availableCredits
                    ));
                }
            }

            tradingService.ensureOrderBookExists(request.getCreditId());

            // Place order
            Order placedOrder = tradingService.placeOrder(request, userId);

            log.info(" Order placed successfully: orderId={}", placedOrder.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(placedOrder);

        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid order: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Failed to place order: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to place order: " + e.getMessage()
            ));
        }
    }


    /**
     * Get user's orders (history + open orders)
     */
    @GetMapping("/my-orders")
    public ResponseEntity<?> getMyOrders(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String creditId
    ) {
        try {
            String userId = authentication.getName();

            log.info("📋 Getting orders: userId={}, status={}, creditId={}",
                    userId, status, creditId);

            List<Order> orders;

            if (status != null && creditId != null) {
                orders = orderService.getOrdersByUserIdAndStatusAndCreditId(userId, status, creditId);
            } else if (status != null) {
                orders = orderService.getOrdersByUserIdAndStatus(userId, status);
            } else if (creditId != null) {
                orders = orderService.getOrdersByUserIdAndCreditId(userId, creditId);
            } else {
                orders = orderService.getOrdersByUserId(userId);
            }

            return ResponseEntity.ok(orders);

        } catch (Exception e) {
            log.error("❌ Failed to get orders: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to retrieve orders"
            ));
        }
    }

    /**
     * Get user's open orders only
     */
    @GetMapping("/my-orders/open")
    public ResponseEntity<?> getMyOpenOrders(
            Authentication authentication,
            @RequestParam(required = false) String creditId
    ) {
        try {
            String userId = authentication.getName();

            log.info("📋 Getting open orders: userId={}, creditId={}", userId, creditId);

            List<Order> orders = creditId != null
                    ? orderService.getOrdersByUserIdAndStatusAndCreditId(userId, "OPEN", creditId)
                    : orderService.getOpenOrdersByUserId(userId);

            return ResponseEntity.ok(orders);

        } catch (Exception e) {
            log.error("❌ Failed to get open orders: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to retrieve open orders"
            ));
        }
    }

    /**
     * Cancel order
     */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<?> cancelOrder(
            @PathVariable String orderId,
            Authentication authentication
    ) {
        try {
            String userId = authentication.getName();

            log.info(" Cancelling order: orderId={}, userId={}", orderId, userId);

            // Validate order exists
            Order order = orderService.findById(orderId);
            if (order == null) {
                log.warn("️ Order not found: {}", orderId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "Not Found",
                        "message", "Order not found"
                ));
            }

            // Validate ownership
            if (!order.getUserId().equals(userId)) {
                log.warn("️ Unauthorized cancel attempt: userId={}, orderId={}", userId, orderId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "Forbidden",
                        "message", "Not authorized to cancel this order"
                ));
            }

            // Validate status
            if (!"OPEN".equals(order.getStatus()) && !"PENDING".equals(order.getStatus())) {
                log.warn("️ Cannot cancel order with status: {}", order.getStatus());
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Bad Request",
                        "message", "Order cannot be cancelled. Current status: " + order.getStatus()
                ));
            }

            // Cancel trong matching engine + update DB + unlock balance
            boolean cancelled = tradingService.cancelOrder(order);

            if (cancelled) {
                log.info(" Order cancelled: orderId={}", orderId);
                return ResponseEntity.ok(Map.of(
                        "message", "Order cancelled successfully",
                        "orderId", orderId
                ));
            }

            log.error(" Failed to cancel order: orderId={}", orderId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to cancel order"
            ));

        } catch (Exception e) {
            log.error(" Error cancelling order: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to cancel order: " + e.getMessage()
            ));
        }
    }

    /**
     * Get order by ID
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrderById(
            @PathVariable String orderId,
            Authentication authentication
    ) {
        try {
            String userId = authentication.getName();

            log.info(" Getting order: orderId={}, userId={}", orderId, userId);

            Order order = orderService.findById(orderId);

            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "Not Found",
                        "message", "Order not found"
                ));
            }

            // Validate ownership
            if (!order.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "Forbidden",
                        "message", "Not authorized to view this order"
                ));
            }

            return ResponseEntity.ok(order);

        } catch (Exception e) {
            log.error(" Failed to get order: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to retrieve order"
            ));
        }
    }

    /**
     * Get public orderbook snapshot (không cần auth)
     */
    @GetMapping("/snapshot/{creditId}")
    public ResponseEntity<?> getSnapshot(@PathVariable String creditId) {
        try {
            log.info(" Getting orderbook snapshot: creditId={}", creditId);

            Map<String, Object> snapshot = orderService.getOrderBookSnapshot(creditId);

            if (snapshot == null || snapshot.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "Not Found",
                        "message", "Orderbook not found for credit: " + creditId
                ));
            }

            return ResponseEntity.ok(snapshot);

        } catch (Exception e) {
            log.error(" Failed to get snapshot: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to retrieve orderbook snapshot"
            ));
        }
    }

    /**
     * Get all orderbooks snapshot
     */
    @GetMapping("/snapshots")
    public ResponseEntity<?> getAllSnapshots() {
        try {
            log.info(" Getting all orderbook snapshots");

            Map<String, Map<String, Object>> snapshots = orderService.getAllOrderBookSnapshots();
            return ResponseEntity.ok(snapshots);

        } catch (Exception e) {
            log.error(" Failed to get snapshots: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to retrieve orderbook snapshots"
            ));
        }
    }
}
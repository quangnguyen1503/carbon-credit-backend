package com.example.carbon_credit.Service;

import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Entity.Trade;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WsService {

    private final SimpMessagingTemplate template;

    // Broadcast new order to /topic/orderbook
    public void broadcastOrder(Order order) {
        template.convertAndSend("/topic/orderbook", Map.of("type", "update", "order", order));
    }

    // Broadcast trade to /topic/trades
    public void broadcastTrade(Trade trade) {
        template.convertAndSend("/topic/trades", trade);
    }

    // Broadcast order update (e.g., filled/partial, remove)
    public void broadcastOrderUpdate(Order order) {
        if ("FILLED".equals(order.getStatus())) {
            // Remove filled order
            template.convertAndSend("/topic/orderbook", Map.of("type", "remove", "id", order.getId()));
        } else {
            // Update partial/filled
            template.convertAndSend("/topic/orderbook", Map.of("type", "update", "order", order));
        }
    }

    // Broadcast snapshot (full orders list)
    public void broadcastSnapshot(List<Order> buyOrders, List<Order> sellOrders) {
        template.convertAndSend("/topic/orderbook", Map.of("type", "snapshot", "orders", List.of(buyOrders, sellOrders)));
    }
}
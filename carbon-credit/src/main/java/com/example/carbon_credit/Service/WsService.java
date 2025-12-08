package com.example.carbon_credit.Service;

import com.example.carbon_credit.Entity.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WsService {

    private final SimpMessagingTemplate template;

    public void broadcastOrder(Order order) {
        template.convertAndSend("/topic/orderbook", order);
    }

    public void broadcastTrade(Order buy, Order sell, long amount, String tx) {
        Map<String,Object> payload = Map.of(
                "buy", buy,
                "sell", sell,
                "amount", amount,
                "tx", tx
        );

        template.convertAndSend("/topic/trades", payload);
    }
}


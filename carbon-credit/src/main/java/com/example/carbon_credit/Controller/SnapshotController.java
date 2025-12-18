package com.example.carbon_credit.Controller;

import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Service.OrderService;
import com.example.carbon_credit.Service.WsService;  // ← THÊM: Import WsService đúng
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Controller  // ← WS Controller (không phải @RestController, vì chỉ handle message)
public class SnapshotController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private WsService wsService;  // ← THÊM: Inject WsService để broadcast

    // ← FIX: Handle FE publish /app/snapshot, broadcast full to /topic/orderbook
    @MessageMapping("/snapshot")  // Catch message từ FE (STOMP destination /app/snapshot)
    public void requestSnapshot() {  // Không return, chỉ broadcast
        System.out.println("Received /app/snapshot request from FE");  // ← THÊM: Debug log confirm handler hit

        Map<String, Object> snapshotMap = orderService.getSnapshot();  // Get Map từ service

        // ← FIX: Safe cast với generic (tránh raw List warning)
        @SuppressWarnings("unchecked")
        List<Order> buys = (List<Order>) ((List<?>) snapshotMap.get("orders")).get(0);  // Extract buys
        @SuppressWarnings("unchecked")
        List<Order> sells = (List<Order>) ((List<?>) snapshotMap.get("orders")).get(1);  // Extract sells

        wsService.broadcastSnapshot(buys, sells);  // ← FIX: Call trực tiếp từ WsService (không qua getWsService)

        System.out.println("Snapshot requested and broadcasted: " + buys.size() + " buys, " + sells.size() + " sells");  // Debug log
    }
}
package com.example.carbon_credit.Controller;

import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Service.OrderService;
import com.example.carbon_credit.Service.WsService;  // ← THÊM: Import WsService đúng
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Controller
public class SnapshotController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private WsService wsService;


    @MessageMapping("/snapshot")
    public void requestSnapshot() {

        Map<String, Object> snapshotMap = orderService.getSnapshot();

        // Ép kiểu an toàn
        Object ordersObj = snapshotMap.get("orders");
        if (ordersObj instanceof List) {
            List<?> rawList = (List<?>) ordersObj;
            if (rawList.size() >= 2) {
                @SuppressWarnings("unchecked")
                List<Order> buys = (List<Order>) rawList.get(0);
                @SuppressWarnings("unchecked")
                List<Order> sells = (List<Order>) rawList.get(1);

                wsService.broadcastSnapshot(buys, sells); // Hết lỗi
            }
        }
    }
}
package com.example.carbon_credit.Controller;

import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Repository.OrderRepository;
import com.example.carbon_credit.Service.WsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository repo;
    private final WsService ws;

    @PostMapping("/create")
    public Order create(@RequestBody Order order) {
        order.setStatus("OPEN");
        Order saved = repo.save(order);

        ws.broadcastOrder(saved);
        return saved;
    }
}


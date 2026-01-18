package com.example.carbon_credit.Service;

import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExpiryService {

    private final OrderRepository orderRepository;
    private final TradingService tradingService;

    /**
     * Check for expired orders every minute
     */
    @Scheduled(fixedDelay = 60000)
    public void convertExpiredOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore("OPEN", cutoff);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("Found {} expired orders older than {}", expiredOrders.size(), cutoff);

        for (Order order : expiredOrders) {
            try {
                tradingService.expireOrder(order);
            } catch (Exception e) {
                log.error("❌ Failed to process expiry for order {}: {}", order.getId(), e.getMessage());
            }
        }
    }
}

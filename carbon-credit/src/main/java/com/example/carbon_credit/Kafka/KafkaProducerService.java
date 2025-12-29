package com.example.carbon_credit.Kafka;

import com.example.carbon_credit.DTO.PlaceOrderCommand;
import com.example.carbon_credit.DTO.TradeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Gửi lệnh vào topic "orders" (partition theo creditId)
     */
    public void sendOrder(PlaceOrderCommand command) {
        try {
            kafkaTemplate.send("orders", command.getCreditId(), command)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("📤 Order {} sent to Kafka", command.getOrderId());
                        } else {
                            log.error("❌ Failed to send order {}: {}", command.getOrderId(), ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("❌ Kafka send error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send order to Kafka", e);
        }
    }

    /**
     * Gửi danh sách trades vào topic "trades"
     */
    public void sendTrades(List<TradeEvent> trades) {
        trades.forEach(trade -> {
            try {
                kafkaTemplate.send("trades", trade.getCreditId(), trade)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                log.info("📤 Trade {} sent to Kafka", trade.getTradeId());
                            } else {
                                log.error("❌ Failed to send trade {}: {}", trade.getTradeId(), ex.getMessage());
                            }
                        });
            } catch (Exception e) {
                log.error("❌ Kafka send error for trade {}: {}", trade.getTradeId(), e.getMessage());
            }
        });
    }
}

package com.example.carbon_credit.Kafka;

import com.example.carbon_credit.DTO.BlockchainEventDTO;
import com.example.carbon_credit.DTO.PlaceOrderCommandDTO;
import com.example.carbon_credit.DTO.TradeEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendOrder(PlaceOrderCommandDTO command) {
        String key = command.getCreditId();

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send("orders", key, command);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Order sent: {} -> Partition: {}", command.getOrderId(),
                        result.getRecordMetadata().partition());
            } else {
                log.error(" FAILED to send order {}: {}", command.getOrderId(), ex.getMessage());
            }
        });
    }

    public void sendTrades(List<TradeEventDTO> trades) {
        for (TradeEventDTO trade : trades) {
            kafkaTemplate.send("trades", trade.getCreditId(), trade)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error(" FAILED to send trade {}: {}", trade.getTradeId(), ex.getMessage());
                        }
                    });
        }
    }

    /**
     * Send trades synchronously (blocking) to ensure data durability calls.
     */
    public void sendTradesSync(List<TradeEventDTO> trades) throws Exception {
        for (TradeEventDTO trade : trades) {
            try {
                kafkaTemplate.send("trades", trade.getCreditId(), trade).get();
            } catch (Exception e) {
                log.error(" FAILED to send trade sync {}: {}", trade.getTradeId(), e.getMessage());
                throw e;
            }
        }
    }

    public void sendOnChainEvent(BlockchainEventDTO event) {
        try {
            log.info("Sending event to Kafka: {} | TxHash: {}",
                    event.getEventType(),
                    event.getTransactionHash());

            kafkaTemplate.send("onchain-events", event.getTransactionHash(), event);

            log.info("Event sent successfully");

        } catch (Exception e) {
            log.error("Failed to send event to Kafka: {}", e.getMessage(), e);
            throw new RuntimeException("Kafka send failed", e);
        }
    }

    /**
     * Send failed events to Dead Letter Queue for manual review/retry
     */
    public void sendToDeadLetterQueue(BlockchainEventDTO event, String errorMessage) {
        try {
            // Add error info to event
            event.setErrorMessage(errorMessage);
            event.setFailedAt(java.time.LocalDateTime.now().toString());

            kafkaTemplate.send("onchain-events-dlq", event.getTransactionHash(), event).get();

            log.warn("Event {} sent to DLQ. Error: {}", event.getTransactionHash(), errorMessage);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send to DLQ: {}", e.getMessage(), e);
            throw new RuntimeException("DLQ send failed", e);
        }
    }
}
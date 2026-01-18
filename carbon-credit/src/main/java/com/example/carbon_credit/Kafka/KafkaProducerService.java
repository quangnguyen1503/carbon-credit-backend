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
        // Sử dụng creditId làm key để đảm bảo partition ordering
        String key = command.getCreditId();

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send("orders", key, command);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Order sent: {} -> Partition: {}", command.getOrderId(), result.getRecordMetadata().partition());
            } else {
                // Đây là lỗi nghiêm trọng: Lệnh không vào được hàng đợi
                log.error(" FAILED to send order {}: {}", command.getOrderId(), ex.getMessage());
                // Cần có cơ chế fallback hoặc alert ở đây (ví dụ: trả lỗi 500 ngay cho user nếu đang trong request sync)
            }
        });
    }

    public void sendTrades(List<TradeEventDTO> trades) {
        for (TradeEventDTO trade : trades) {
            // Key vẫn là creditId để đảm bảo các trade của cùng 1 cặp được xử lý tuần tự nếu cần
            kafkaTemplate.send("trades", trade.getCreditId(), trade)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error(" FAILED to send trade {}: {}", trade.getTradeId(), ex.getMessage());
                            // TODO: Lưu trade này vào file/DB tạm để retry thủ công (Emergency Persistence)
                        }
                    });
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
}
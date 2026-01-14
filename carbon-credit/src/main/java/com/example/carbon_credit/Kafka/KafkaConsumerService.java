package com.example.carbon_credit.Kafka;

import com.example.carbon_credit.DTO.BlockchainEventDTO;
import com.example.carbon_credit.DTO.PlaceOrderCommandDTO;
import com.example.carbon_credit.DTO.TradeEventDTO;
import com.example.carbon_credit.MatchingEngine.MatchingEngine;
import com.example.carbon_credit.Service.PersistenceService;
import com.example.carbon_credit.Service.SettlementService;
import com.example.carbon_credit.Service.WsService;
import com.example.carbon_credit.Service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final MatchingEngine matchingEngine;
    private final KafkaProducerService kafkaProducerService;
    private final WsService wsService;
    private final SettlementService settlementService;
    private final PersistenceService persistenceService;
    private final WalletService walletService;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100;

    /**
     * Consumer 1: Xử lý Khớp lệnh (Core Logic)
     * Quan trọng: concurrency phải <= số partitions của topic 'orders'
     */
    @KafkaListener(topics = "orders", groupId = "carbon-matching-group", // Tách riêng group cho matching
            concurrency = "3", containerFactory = "kafkaListenerContainerFactory" // Cần config factory có ackMode =
                                                                                  // MANUAL_IMMEDIATE
    )
    public void consumeOrder(PlaceOrderCommandDTO command,
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        long startTime = System.nanoTime();

        // Log rõ ràng để debug thứ tự xử lý
        log.info("[P-{}|O-{}] Processing Order: {} | Type: {} | Credit: {}",
                partition, offset, command.getOrderId(), command.getOrderType(), command.getCreditId());

        try {
            if (command.getPrice() == null || command.getAmount() <= 0) {
                throw new IllegalArgumentException("Price or Amount is invalid");
            }

            // 1. GỌI MATCHING ENGINE (In-Memory)
            List<TradeEventDTO> trades = matchingEngine.processOrder(command);

            // 2. Xử lý kết quả khớp
            if (trades != null && !trades.isEmpty()) {
                log.info(" Matched {} trades for order {}", trades.size(), command.getOrderId());
                // Gửi trades đi để các service khác xử lý (Async)
                kafkaProducerService.sendTrades(trades);
            } else {
                log.info(" Order {} added to OrderBook (No match)", command.getOrderId());
            }

            // 3. Cập nhật UI (OrderBook Snapshot)
            // Lưu ý: Có thể đẩy việc này ra một topic riêng 'market-data' để giảm tải cho
            // thread matching
            broadcastOrderBookChange(command.getCreditId());

            // 4. Commit offset khi mọi thứ thành công
            ack.acknowledge();

            // Monitor hiệu năng
            long duration = (System.nanoTime() - startTime) / 1000;
            log.debug("Processing time: {} us", duration);

        } catch (Exception e) {
            log.error("CRITICAL ERROR processing order {}: {}", command.getOrderId(), e.getMessage(), e);
            throw e;
        } finally {
            ack.acknowledge();
        }
    }

    /**
     * Consumer 2: Xử lý Hậu kỳ (Persistence, Settlement, Notification)
     * Group ID nên khác với Matching để scale độc lập
     */
    @KafkaListener(topics = "trades", groupId = "carbon-post-trade-group")
    public void consumeTrade(TradeEventDTO trade, Acknowledgment ack) {
        try {
            log.info("Post-processing Trade: {}", trade.getTradeId());

            // 1. Lưu vào Database (QUAN TRỌNG NHẤT - Persistence)
            persistenceService.saveHistoricalTrade(trade);

            // 2. Đưa vào lô Quyết toán (Settlement)
            settlementService.addTradeToBatch(trade);

            // 3. Thông báo Realtime (WebSocket)
            try {
                wsService.broadcastTrade(trade);
                wsService.broadcastPriceUpdate(trade.getCreditId(), trade.getPrice(), trade.getAmount());
            } catch (Exception wsEx) {
                log.warn("WebSocket broadcast failed for trade {}: {}", trade.getTradeId(), wsEx.getMessage());
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error(" Error persisting/settling trade {}: {}", trade.getTradeId(), e.getMessage(), e);
            // Ném lỗi để kích hoạt Retry/DLQ. Không được làm mất Trade!
            throw new RuntimeException("Failed to process trade " + trade.getTradeId(), e);
        }
    }

    @KafkaListener(
        topics = "blockchain-events",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeBlockchainEvent(BlockchainEventDTO event, Acknowledgment ack) {
        
        int attempt = 0;
        boolean success = false;
        
        while (attempt < MAX_RETRY_ATTEMPTS && !success) {
            try {
                attempt++;
                
                log.info("📨 Received event: {} | TxHash: {} (Attempt {}/{})", 
                    event.getEventType(), 
                    event.getTransactionHash(),
                    attempt,
                    MAX_RETRY_ATTEMPTS);

                processEvent(event);
                
                success = true;
                
                if (ack != null) {
                    ack.acknowledge();
                }
                
                log.info("✅ Successfully processed event: {}", event.getTransactionHash());
                
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("⚠️ Optimistic locking conflict (attempt {}/{}): {}", 
                    attempt, MAX_RETRY_ATTEMPTS, e.getMessage());
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                        log.info("🔄 Retrying event: {}", event.getTransactionHash());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("❌ Retry interrupted");
                        break;
                    }
                } else {
                    log.error("❌ Failed after {} attempts: {}", MAX_RETRY_ATTEMPTS, e.getMessage());
                    // Optionally: Send to DLQ (Dead Letter Queue)
                    if (ack != null) {
                        ack.acknowledge(); // Acknowledge to prevent infinite retry
                    }
                }
                
            } catch (Exception e) {
                log.error("❌ Failed to process event {}: {}", 
                    event.getTransactionHash(), 
                    e.getMessage(), 
                    e);
                
                if (ack != null) {
                    ack.acknowledge(); // Acknowledge to prevent stuck message
                }
                break;
            }
        }
    }

    @Transactional
    private void processEvent(BlockchainEventDTO event) {
        String eventType = event.getEventType();

        switch (eventType) {
            case "ADMIN_ADDED" -> {
                walletService.handleAdminAdded(event);
            }
            case "ADMIN_REMOVED" -> {
                walletService.handleAdminRemoved(event);
            }
            case "GOVERNMENT_ADDED" -> {
                walletService.handleGovernmentAdded(event);
            }
            case "GOVERNMENT_REMOVED" -> {
                walletService.handleGovernmentRemoved(event);
            }
            case "ORGANIZATION_VERIFIED" -> {
                walletService.handleVerifierAdded(event);
            }
            case "ORGANIZATION_REVOKED" -> {
                walletService.handleVerifierRemoved(event);
            }
            case "PROJECT_APPROVED" -> {
                walletService.handleProjectApproved(event);
            }
            case "PROJECT_APPROVED" -> {
                walletService.handleProjectRevolked(event);
            }
            case "CREDIT_MINTED" -> {
                walletService.handleCreditMinted(event);
            }
            case "CREDIT_RETIRED" -> {
                walletService.handleCreditRetired(event);
            }
            case "CERTIFICATE_MINTED" -> {
                walletService.handleCertificateMinted(event);
            }
            case "BATCH_CERTIFICATE_RETIRED" -> {
                walletService.handleBatchCeritificateRetired(event);
            }
            case "NATIVE_DEPOSITED" -> {
                walletService.handleNativeDeposited(event);
            }
            case "NATIVE_WITHDRAWN" -> {
                walletService.handleNativeWithdraw(event);
            }
            case "CREDIT_DEPOSIT" -> {
                walletService.handleCreditDeposited(event);
            }
            case "CREDIT_WITHDRAWN" -> {
                walletService.handleCreditWithdraw(event);
            }
            case "TRADE_SETTLED" -> {
                walletService.handleTradeSettled(event);
            }
            default -> {
                log.warn("⚠️ Unhandled event type: {}", eventType);
            }
        }
    }

    private void broadcastOrderBookChange(String creditId) {
        try {
            Map<String, Object> snapshot = matchingEngine.getOrderBook(creditId);
            if (snapshot != null) {
                wsService.broadcastOrderBookUpdate(creditId, snapshot);
            }
        } catch (Exception e) {
            log.warn("Failed to broadcast orderbook update: {}", e.getMessage());
        }
    }
}
package com.example.carbon_credit.Kafka;

import com.example.carbon_credit.DTO.BlockchainEventDTO;
import com.example.carbon_credit.DTO.PlaceOrderCommandDTO;
import com.example.carbon_credit.DTO.TradeEventDTO;
import com.example.carbon_credit.Entity.FailedEvent;
import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.MatchingEngine.MatchingEngine;
import com.example.carbon_credit.Repository.FailedEventRepository;
import com.example.carbon_credit.Repository.OrderRepository;
import com.example.carbon_credit.Service.*;
import com.example.carbon_credit.Service.impl.ProjectServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 100;
    private final MatchingEngine matchingEngine;
    private final KafkaProducerService kafkaProducerService;
    private final WsService wsService;
    private final SettlementService settlementService;
    private final PersistenceService persistenceService;
    private final WalletService walletService;
    private final RoleRequestService roleRequestService;
    private final ProjectServiceImpl projectServiceImpl;
    private final CarbonCreditService carbonCreditService;
    private final CertificateService certificateService;
    private final OrderRepository orderRepository;
    private final FailedEventRepository failedEventRepository;
    private final OhlcService ohlcService;

    /**
     * Consumer 1: Xử lý Khớp lệnh (Core Logic)
     * Quan trọng: concurrency phải <= số partitions của topic 'orders'
     */
    @KafkaListener(topics = "orders", groupId = "carbon-matching-group", concurrency = "3", containerFactory = "kafkaListenerContainerFactory")
    public void consumeOrder(PlaceOrderCommandDTO command, Acknowledgment ack, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition, @Header(KafkaHeaders.OFFSET) long offset) throws Exception {

        log.info(" [Kafka CONSUMER] [P-{}|O-{}] RECEIVED Order: {} | Type: {} | CreditId: {}", partition, offset, command.getOrderId(), command.getOrderType(), command.getCreditId());

        try {
            if (command.getPrice() == null || command.getAmount() <= 0) {
                throw new IllegalArgumentException("Price or Amount is invalid");
            }

            // 0. CHECK STATUS (Race Condition Guard)
            Order order = orderRepository.findById(command.getOrderId()).orElse(null);
            if (order != null && "CANCELLED".equals(order.getStatus())) {
                log.warn(" Skipping order {} (Status: CANCELLED)", command.getOrderId());
                ack.acknowledge();
                return;
            }

            // 1. GỌI MATCHING ENGINE (In-Memory)
            List<TradeEventDTO> trades = matchingEngine.processOrder(command);

            // 2. Xử lý kết quả khớp
            if (trades != null && !trades.isEmpty()) {
                log.info(" Matched {} trades for order {}", trades.size(), command.getOrderId());
                kafkaProducerService.sendTradesSync(trades);
            } else {
                log.info(" Order {} added to OrderBook (No match)", command.getOrderId());
            }

            // 3. Cập nhật UI (OrderBook Snapshot)
            broadcastOrderBookChange(command.getCreditId());

            // 4. Commit offset khi mọi thứ thành công
            ack.acknowledge();

        } catch (Exception e) {
            log.error("CRITICAL ERROR processing order {}: {}", command.getOrderId(), e.getMessage(), e);
            throw e;
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
                ohlcService.updateOhlcFromTrade(trade.getCreditId(), trade.getPrice(), trade.getAmount());
                wsService.broadcastPriceUpdate(trade.getCreditId(), trade.getPrice(), trade.getAmount());
            } catch (Exception wsEx) {
                log.warn("WebSocket broadcast failed for trade {}: {}", trade.getTradeId(), wsEx.getMessage());
            }

            ack.acknowledge();

        } catch (Exception e) {
            log.error(" Error persisting/settling trade {}: {}", trade.getTradeId(), e.getMessage(), e);
            throw new RuntimeException("Failed to process trade " + trade.getTradeId(), e);
        }
    }

    @KafkaListener(topics = "onchain-events-dlq", groupId = "dlq-processor-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeDlqEvent(BlockchainEventDTO event, Acknowledgment ack) {
        try {
            log.warn("Processing DLQ event: {} | Type: {} | Error: {}", event.getTransactionHash(), event.getEventType(), event.getErrorMessage());

            // 1. Save to database for admin review
            FailedEvent failedEvent = FailedEvent.builder().id(UUID.randomUUID().toString()).transactionHash(event.getTransactionHash()).eventType(event.getEventType()).contractAddress(event.getContractAddress()).blockNumber(event.getBlockNumber() != null ? event.getBlockNumber().longValue() : null).eventData(event.getData()).errorMessage(event.getErrorMessage()).failedAt(event.getFailedAt() != null ? LocalDateTime.parse(event.getFailedAt()) : LocalDateTime.now()).status("PENDING_REVIEW").createdAt(LocalDateTime.now()).build();

            failedEventRepository.save(failedEvent);

            // 2. Notify admins via WebSocket
            wsService.notify("Blockchain Event Failed", String.format("Event %s (%s) failed: %s", event.getEventType(), event.getTransactionHash().substring(0, 10) + "...", event.getErrorMessage()), "ERROR", List.of("ADMIN"), null);

            log.info("DLQ event saved to database: {}", failedEvent.getId());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process DLQ event: {}", e.getMessage(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "onchain-events", groupId = "carbon-market-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeBlockchainEvent(BlockchainEventDTO event, Acknowledgment ack) {

        int attempt = 0;
        boolean success = false;
        Exception lastException = null;

        while (attempt < MAX_RETRY_ATTEMPTS && !success) {
            try {
                attempt++;

                log.info(" Received event: {} | TxHash: {} (Attempt {}/{})", event.getEventType(), event.getTransactionHash(), attempt, MAX_RETRY_ATTEMPTS);

                processEvent(event);

                success = true;

                if (ack != null) {
                    ack.acknowledge();
                }

                log.info(" Successfully processed event: {}", event.getTransactionHash());

            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn(" Optimistic locking conflict (attempt {}/{}): {}", attempt, MAX_RETRY_ATTEMPTS, e.getMessage());
                lastException = e;

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                        log.info(" Retrying event: {}", event.getTransactionHash());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error(" Retry interrupted");
                        break;
                    }
                }

            } catch (Exception e) {
                log.error(" Failed to process event {}: {}", event.getTransactionHash(), e.getMessage(), e);
                lastException = e;
                break;
            }
        }

        if (!success && lastException != null) {
            log.error(" Event {} failed after {} attempts. Sending to DLQ.", event.getTransactionHash(), attempt);
            try {
                kafkaProducerService.sendToDeadLetterQueue(event, lastException.getMessage());
                if (ack != null) {
                    ack.acknowledge();
                }
            } catch (Exception dlqEx) {
                log.error(" CRITICAL: Failed to send to DLQ. Event will be retried: {}", dlqEx.getMessage());
            }
        }
    }

    private void processEvent(BlockchainEventDTO event) {
        String eventType = event.getEventType();

        switch (eventType) {
            case "ADMIN_ADDED" -> {
                roleRequestService.handleAdminAdded(event);
            }
            case "ADMIN_REMOVED" -> {
                roleRequestService.handleAdminRemoved(event);
            }
            case "GOVERNMENT_ADDED" -> {
                roleRequestService.handleGovernmentAdded(event);
            }
            case "GOVERNMENT_REMOVED" -> {
                roleRequestService.handleGovernmentRemoved(event);
            }
            case "ORGANIZATION_VERIFIED" -> {
                roleRequestService.handleVerifierAdded(event);
            }
            case "ORGANIZATION_REVOKED" -> {
                roleRequestService.handleVerifierRemoved(event);
            }
            case "PROJECT_APPROVED" -> {
                projectServiceImpl.handleProjectApproved(event);
            }
            case "CREDIT_MINTED" -> {
                carbonCreditService.handleCreditMinted(event);
            }
            case "BATCH_CERTIFICATE_RETIRED" -> {
                certificateService.handleBatchCeritificateRetired(event);
            }
            case "NATIVE_DEPOSITED" -> {
                walletService.handleNativeDeposited(event);
            }
            case "NATIVE_WITHDRAWN" -> {
                walletService.handleNativeWithdraw(event);
            }
            case "CREDIT_DEPOSITED" -> {
                walletService.handleCreditDeposited(event);
            }
            case "CREDIT_WITHDRAWN" -> {
                walletService.handleCreditWithdraw(event);
            }
            case "TRADE_SETTLED" -> {
                walletService.handleTradeSettled(event);
            }
            case "BALANCE_LOCKED" -> {
                walletService.handleBalanceLocked(event);
            }
            case "BALANCE_UNLOCKED" -> {
                walletService.handleBalanceUnlocked(event);
            }
            default -> {
                log.warn(" Unhandled event type: {}", eventType);
            }
        }
    }

    private void broadcastOrderBookChange(String creditId) {
        try {
            long start = System.currentTimeMillis();
            Map<String, Object> snapshot = matchingEngine.getOrderBook(creditId);
            if (snapshot != null) {
                log.debug(" Snapshot retrieved for {}. Sending to WsService...", creditId);
                wsService.broadcastOrderBookUpdate(creditId, snapshot);
                log.debug(" OrderBook Broadcast sent to WS (took {}ms)", System.currentTimeMillis() - start);
            } else {
                log.warn(" Snapshot is NULL for creditId: {}. Skipping broadcast.", creditId);
            }
        } catch (Exception e) {
            log.error(" Failed to broadcast orderbook update for {}: {}", creditId, e.getMessage(), e);
        }
    }
}
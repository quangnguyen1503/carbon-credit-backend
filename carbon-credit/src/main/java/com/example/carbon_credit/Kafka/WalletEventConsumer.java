package com.example.carbon_credit.Kafka;

import com.example.carbon_credit.DTO.BlockchainEventDTO;
import com.example.carbon_credit.Service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletEventConsumer {

    private final WalletService walletService;

    /**
     * Lắng nghe Kafka topic "onchain-events"
     * Xử lý các events liên quan đến Wallet: Deposit, Withdraw, Lock, Unlock
     */
    @KafkaListener(topics = "onchain-events", groupId = "wallet-consumer-group")
    public void consumeBlockchainEvent(BlockchainEventDTO event) {
        try {
            log.info("Received event: {} | TxHash: {}", event.getEventType(), event.getTransactionHash());

            switch (event.getEventType()) {
                case "NATIVE_DEPOSITED":
                    walletService.handleNativeDeposited(event);
                    break;

                case "NATIVE_WITHDRAWN":
                    walletService.handleNativeWithdraw(event);
                    break;

                case "CREDIT_DEPOSITED":
                    walletService.handleCreditDeposited(event);
                    break;

                case "CREDIT_WITHDRAWN":
                    walletService.handleCreditWithdraw(event);
                    break;

                case "BALANCE_LOCKED":
                    walletService.handleBalanceLocked(event);
                    break;

                case "BALANCE_UNLOCKED":
                    walletService.handleBalanceUnlocked(event);
                    break;

                case "TRADE_SETTLED":
                case "BATCH_SETTLED":
                    walletService.handleTradeSettled(event);
                    break;

                default:
                    // Ignore other events (admin, project, etc.)
                    log.debug("Skipping event: {}", event.getEventType());
            }

        } catch (Exception e) {
            log.error("Failed to process event {}: {}", event.getTransactionHash(), e.getMessage());
            // TODO: Send to DLQ (Dead Letter Queue) for retry
        }
    }
}

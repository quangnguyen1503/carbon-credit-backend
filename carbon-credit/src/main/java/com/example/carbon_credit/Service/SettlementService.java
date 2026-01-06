package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.TradeDTO;
import com.example.carbon_credit.DTO.TradeEventDTO;
import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final ContractService contractService;
    private final OrderRepository orderRepository;  // ← THÊM
    private final List<TradeEventDTO> batchQueue = new ArrayList<>();
    private final AtomicLong batchIdCounter = new AtomicLong(1);
    private static final int BATCH_SIZE = 10;

    /**
     * Add trade to batch queue
     */
    public synchronized void addTradeToBatch(TradeEventDTO trade) {
        batchQueue.add(trade);
        log.info("📦 Added trade {} to batch (size: {})", trade.getTradeId(), batchQueue.size());
        
        // Auto settle nếu batch đủ 10 trades
        if (batchQueue.size() >= BATCH_SIZE) {
            settleBatch();
        }
    }

    /**
     * Settle batch every 30 seconds (hoặc khi đủ 10 trades)
     */
    @Scheduled(fixedDelay = 30000)
    public synchronized void settleBatch() {
        if (batchQueue.isEmpty()) return;

        long batchId = batchIdCounter.getAndIncrement();
        log.info("⛓️ Settling batch {} with {} trades...", batchId, batchQueue.size());

        try {
            List<TradeDTO> trades = new ArrayList<>();
            List<String> buyOrderIds = new ArrayList<>();
            List<String> sellOrderIds = new ArrayList<>();

            for (TradeEventDTO tradeEvent : batchQueue) {
                // 🔍 LẤY WALLET ADDRESS TỪ ORDER
                Order buyOrder = orderRepository.findById(tradeEvent.getBuyOrderId())
                        .orElseThrow(() -> new IllegalArgumentException(
                            "Buy order not found: " + tradeEvent.getBuyOrderId()));
                
                Order sellOrder = orderRepository.findById(tradeEvent.getSellOrderId())
                        .orElseThrow(() -> new IllegalArgumentException(
                            "Sell order not found: " + tradeEvent.getSellOrderId()));

                // userId chính là wallet address
                String buyerAddress = buyOrder.getUserId();
                String sellerAddress = sellOrder.getUserId(); 

                // Calculate amounts
                BigInteger creditTokenId = new BigInteger(tradeEvent.getCreditId());
                BigInteger creditAmount = BigInteger.valueOf(tradeEvent.getAmount());
                BigInteger priceWei = tradeEvent.getPrice()
                        .multiply(new BigDecimal("1000000000000000000"))
                        .toBigInteger();
                BigInteger totalValue = priceWei.multiply(creditAmount);

                // Create trade struct
                TradeDTO trade = TradeDTO.builder()
                        .buyer(buyerAddress)      // ← Wallet address
                        .seller(sellerAddress)    // ← Wallet address
                        .creditTokenId(creditTokenId)
                        .amount(creditAmount)
                        .totalValue(totalValue)
                        .build();

                trades.add(trade);
                buyOrderIds.add(tradeEvent.getBuyOrderId());
                sellOrderIds.add(tradeEvent.getSellOrderId());
            }

            // Call smart contract batch settlement
            contractService.processBatchSettlement(
                    BigInteger.valueOf(batchId),
                    trades,
                    buyOrderIds,
                    sellOrderIds
            );

            log.info("✅ Batch {} settled on-chain with {} trades", batchId, batchQueue.size());
            batchQueue.clear();

        } catch (Exception e) {
            log.error("❌ Failed to settle batch {}: {}", batchId, e.getMessage(), e);
            // Keep trades in queue for retry
        }
    }
}
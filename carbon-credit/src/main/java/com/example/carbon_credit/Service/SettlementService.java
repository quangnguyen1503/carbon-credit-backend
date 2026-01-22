package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.TradeDTO;
import com.example.carbon_credit.DTO.TradeEventDTO;
import com.example.carbon_credit.Entity.Order;
import com.example.carbon_credit.Entity.Trade;
import com.example.carbon_credit.Repository.OrderRepository;
import com.example.carbon_credit.Repository.TradeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private static final int BATCH_SIZE = 10;
    private final ContractService contractService;
    private final WsService wsService;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final List<TradeEventDTO> batchQueue = new ArrayList<>();
    private final AtomicLong batchIdCounter = new AtomicLong(1);

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
    public void settleBatch() {

        List<TradeEventDTO> currentBatch;

        synchronized (this) {
            if (batchQueue.isEmpty())
                return;
            currentBatch = new ArrayList<>(batchQueue);
            batchQueue.clear();
        }

        long batchId = batchIdCounter.getAndIncrement();
        log.info("⛓️ Settling batch {} with {} trades...", batchId, currentBatch);

        batchQueue.clear();
        List<TradeDTO> trades = new ArrayList<>();
        List<String> buyOrderIds = new ArrayList<>();
        List<String> sellOrderIds = new ArrayList<>();
        List<TradeEventDTO> validEvents = new ArrayList<>();
        List<TradeEventDTO> invalidEvents = new ArrayList<>();

        try {
            for (TradeEventDTO tradeEvent : currentBatch) {
                try {
                    Order buyOrder = orderRepository.findById(tradeEvent.getBuyOrderId()).orElseThrow(
                            () -> new IllegalArgumentException("Buy order not found: " + tradeEvent.getBuyOrderId()));

                    Order sellOrder = orderRepository.findById(tradeEvent.getSellOrderId()).orElseThrow(
                            () -> new IllegalArgumentException("Sell order not found: " + tradeEvent.getSellOrderId()));

                    // userId chính là wallet address
                    String buyerAddress = buyOrder.getUserId().trim().toLowerCase();
                    String sellerAddress = sellOrder.getUserId().trim().toLowerCase();

                    String onChainOwner = contractService.getLockedBalanceOwner(tradeEvent.getBuyOrderId());

                    log.info("DETECTIVE MODE - Order: {}", tradeEvent.getBuyOrderId());
                    log.info("Java DB Buyer:   {}", buyerAddress);
                    log.info(" On-Chain Owner:  {}", onChainOwner);

                    if (!buyerAddress.equals(onChainOwner)) {
                        log.error("MISMATCH DETECTED! Java says buyer is {}, but Blockchain says lock belongs to {}",
                                buyerAddress, onChainOwner);
                        invalidEvents.add(tradeEvent);
                        continue;
                    }

                    // Calculate amounts
                    BigInteger creditTokenId = new BigInteger(tradeEvent.getCreditId());
                    BigInteger creditAmount = BigInteger.valueOf(tradeEvent.getAmount());
                    BigInteger priceWei = tradeEvent.getPrice().multiply(new BigDecimal("1000000000000000000"))
                            .toBigInteger();
                    BigInteger totalValue = priceWei.multiply(creditAmount);

                    // Create trade struct
                    TradeDTO trade = TradeDTO.builder().buyer(buyerAddress) // ← Wallet address
                            .seller(sellerAddress) // ← Wallet address
                            .creditTokenId(creditTokenId).creditAmount(creditAmount).totalValue(totalValue).build();

                    trades.add(trade);
                    buyOrderIds.add(tradeEvent.getBuyOrderId());
                    sellOrderIds.add(tradeEvent.getSellOrderId());
                    validEvents.add(tradeEvent);
                } catch (Exception e) {
                    log.error("⚠️ Error preparing trade: {}", e.getMessage());
                    invalidEvents.add(tradeEvent);
                }
            }

            // 2. Xử lý các lệnh lỗi (Hoàn tiền ngay)
            if (!invalidEvents.isEmpty()) {
                handleFailedBatch(invalidEvents, "Validation Failed");
            }

            if (trades.isEmpty()) {
                log.warn("⚠️ No valid trades to send.");
                return;
            }

            // Call smart contract batch settlement
            TransactionReceipt receipt = contractService.processBatchSettlement(BigInteger.valueOf(batchId), trades,
                    buyOrderIds, sellOrderIds);
            String txHash = receipt.getTransactionHash();

            // ✅ UPDATE DATABASE: Save Trade + Update Order Status (Atomic)
            saveSettledTradesToDB(validEvents, txHash);

        } catch (Exception e) {
            log.error("❌ Failed to settle batch {}: {}", batchId, e.getMessage(), e);
            if (!validEvents.isEmpty()) {
                handleFailedBatch(validEvents, "On-Chain Transaction Failed: " + e.getMessage());
            }
        }

    }

    @Transactional
    protected void saveSettledTradesToDB(List<TradeEventDTO> tradeEvents, String txHash) {
        List<Trade> tradesToSave = new ArrayList<>();

        Map<String, Order> ordersMap = new HashMap<>();

        for (TradeEventDTO event : tradeEvents) {
            // 1. Save Trade History
            Trade trade = Trade.builder().id(event.getTradeId()).buyOrderId(event.getBuyOrderId())
                    .sellOrderId(event.getSellOrderId()).creditId(event.getCreditId()).amount(event.getAmount())
                    .price(event.getPrice()).totalValue(event.getTotalValue()).txHash(txHash)
                    .tradeAt(LocalDateTime.now()).status("SETTLED").build();
            tradesToSave.add(trade);

            // 2. Update Buy Order
            Order buyOrder = ordersMap.computeIfAbsent(event.getBuyOrderId(),
                    id -> orderRepository.findById(id).orElse(null));
            if (buyOrder != null) {
                updateOrderState(buyOrder, event.getAmount());
            }

            // 3. Update Sell Order
            Order sellOrder = ordersMap.computeIfAbsent(event.getSellOrderId(),
                    id -> orderRepository.findById(id).orElse(null));
            if (sellOrder != null) {
                updateOrderState(sellOrder, event.getAmount());
            }
        }

        tradeRepository.saveAll(tradesToSave);
        orderRepository.saveAll(ordersMap.values()); // Lưu tất cả các order đã update
        log.info("💾 DB Updated: {} Trades Settled", tradesToSave.size());
    }

    private void updateOrderState(Order order, int tradeAmount) {
        int newRemaining = order.getRemainingAmount() - tradeAmount;
        order.setRemainingAmount(Math.max(0, newRemaining));

        if (order.getRemainingAmount() == 0) {
            order.setStatus("SETTLEMENT");
            order.setUpdatedAt(LocalDateTime.now());
            log.info(" ✅ Order {} -> SETTLEMENT", order.getId());
        } else {
            order.setStatus("PARTIALLY_FILLED");
            order.setUpdatedAt(LocalDateTime.now());
            log.info("   🔄 Order {} -> Remaining: {}", order.getId(), newRemaining);
        }
    }

    private void handleFailedBatch(List<TradeEventDTO> failedTrades, String errorReason) {
        log.warn("⚠️ Rolling back {} trades. Reason: {}", failedTrades.size(), errorReason);

        for (TradeEventDTO trade : failedTrades) {
            // Với mỗi giao dịch thất bại, ta phải mở khóa cho cả Lệnh Mua và Lệnh Bán

            // 1. Mở khóa Lệnh Mua (Trả tiền Native lại cho Buyer)
            unlockOrderOnChain(trade.getBuyOrderId());
            updateOrderStatusToFailed(trade.getBuyOrderId(), errorReason);

            // 2. Mở khóa Lệnh Bán (Trả Tín chỉ lại cho Seller)
            unlockOrderOnChain(trade.getSellOrderId());
            updateOrderStatusToFailed(trade.getSellOrderId(), errorReason);
        }
    }

    private void unlockOrderOnChain(String orderId) {
        try {
            log.info("🔓 Requesting Unlock for Order: {}", orderId);
            contractService.unlockBalance(orderId);
        } catch (Exception e) {
            log.error("❌ CRITICAL: Failed to unlock order {} on-chain. Admin attention required!", orderId, e);
        }
    }

    private void updateOrderStatusToFailed(String orderId, String reason) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus("FAILED");
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            wsService.notifyOrderFailure(order.getUserId(), order.getId(), order.getCreditId(),
                    "Settlement transaction failed on Blockchain. Funds unlocked." + reason);
        });
    }
}
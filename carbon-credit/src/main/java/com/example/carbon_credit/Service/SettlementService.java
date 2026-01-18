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
    public synchronized void settleBatch() {
        if (batchQueue.isEmpty())
            return;

        long batchId = batchIdCounter.getAndIncrement();
        log.info("⛓️ Settling batch {} with {} trades...", batchId, batchQueue.size());

        try {
            List<TradeDTO> trades = new ArrayList<>();
            List<String> buyOrderIds = new ArrayList<>();
            List<String> sellOrderIds = new ArrayList<>();

            for (TradeEventDTO tradeEvent : batchQueue) {
                // 🔍 LẤY WALLET ADDRESS TỪ ORDER
                Order buyOrder = orderRepository.findById(tradeEvent.getBuyOrderId()).orElseThrow(
                        () -> new IllegalArgumentException("Buy order not found: " + tradeEvent.getBuyOrderId()));

                Order sellOrder = orderRepository.findById(tradeEvent.getSellOrderId()).orElseThrow(
                        () -> new IllegalArgumentException("Sell order not found: " + tradeEvent.getSellOrderId()));

                // userId chính là wallet address
                String buyerAddress = buyOrder.getUserId().trim().toLowerCase();
                String sellerAddress = sellOrder.getUserId().trim().toLowerCase();

                String onChainOwner = contractService.getLockedBalanceOwner(tradeEvent.getBuyOrderId());

                log.info("🕵️‍♂️ DETECTIVE MODE - Order: {}", tradeEvent.getBuyOrderId());
                log.info("   👉 Java DB Buyer:   {}", buyerAddress);
                log.info("   👉 On-Chain Owner:  {}", onChainOwner);

                if (!buyerAddress.equals(onChainOwner)) {
                    log.error("🚨 MISMATCH DETECTED! Java says buyer is {}, but Blockchain says lock belongs to {}",
                            buyerAddress, onChainOwner);
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
            }

            log.info("📝 PREPARING BATCH SETTLEMENT:");
            log.info("Batch ID: {}", batchId);
            log.info("Trades Count: {}", trades.size());
            for (int i = 0; i < trades.size(); i++) {
                log.info("  Trade #{}: Buyer={} Seller={} Token={} Amt={} Val={}",
                        i, trades.get(i).getBuyer(), trades.get(i).getSeller(),
                        trades.get(i).getCreditTokenId(), trades.get(i).getCreditAmount(),
                        trades.get(i).getTotalValue());
                log.info("  BuyOrder: {}", buyOrderIds.get(i));
                log.info("  SellOrder: {}", sellOrderIds.get(i));
            }

            // Call smart contract batch settlement
            TransactionReceipt receipt = contractService.processBatchSettlement(BigInteger.valueOf(batchId), trades, buyOrderIds, sellOrderIds);
            String txHash = receipt.getTransactionHash();

            log.info("✅ Batch {} settled on-chain with {} trades", batchId, batchQueue.size());

            saveSettledTradesToDB(batchQueue, txHash);

            batchQueue.clear();

        } catch (Exception e) {
            log.error("❌ Failed to settle batch {}: {}", batchId, e.getMessage(), e);
            handleFailedBatch(batchQueue, e.getMessage());
            batchQueue.clear();
        }

    }

    @Transactional
    protected void saveSettledTradesToDB(List<TradeEventDTO> tradeEvents, String txHash) {
        List<Trade> tradesToSave = new ArrayList<>();
        // Dùng Map để đảm bảo nếu 1 Order khớp nhiều lần trong 1 batch, chúng ta không bị ghi đè dữ liệu cũ
        Map<String, Order> ordersMap = new HashMap<>();

        for (TradeEventDTO event : tradeEvents) {
            // A. Tạo Trade (Giữ nguyên)
            Trade trade = Trade.builder()
                    .id(event.getTradeId()).buyOrderId(event.getBuyOrderId()).sellOrderId(event.getSellOrderId())
                    .creditId(event.getCreditId())
                    .amount(event.getAmount())
                    .price(event.getPrice())
                    .totalValue(event.getTotalValue()).txHash(txHash).tradeAt(LocalDateTime.now()).status("SETTLED")
                    .build();
            tradesToSave.add(trade);

            // B. Cập nhật trạng thái và số dư còn lại của BUY ORDER ngay tại đây
            updateOrderInMap(ordersMap, event.getBuyOrderId(), event.getAmount());

            // C. Cập nhật trạng thái và số dư còn lại của SELL ORDER ngay tại đây
            updateOrderInMap(ordersMap, event.getSellOrderId(), event.getAmount());
        }

        tradeRepository.saveAll(tradesToSave);
        orderRepository.saveAll(ordersMap.values()); // Lưu tất cả các order đã update
        log.info("💾 DB Updated: {} Trades, {} Orders modified", tradesToSave.size(), ordersMap.size());
    }

    private void handleFailedBatch(List<TradeEventDTO> failedTrades, String errorReason) {
        log.warn("⚠️ Rolling back {} trades. Reason: {}", failedTrades.size(), errorReason);

        for (TradeEventDTO trade : failedTrades) {
            // Với mỗi giao dịch thất bại, ta phải mở khóa cho cả Lệnh Mua và Lệnh Bán

            // 1. Mở khóa Lệnh Mua (Trả tiền Native lại cho Buyer)
            unlockOrderOnChain(trade.getBuyOrderId());
            updateOrderStatusToFailed(trade.getBuyOrderId(), "SETTLEMENT_FAILED");

            // 2. Mở khóa Lệnh Bán (Trả Tín chỉ lại cho Seller)
            unlockOrderOnChain(trade.getSellOrderId());
            updateOrderStatusToFailed(trade.getSellOrderId(), "SETTLEMENT_FAILED");
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

            wsService.notifyOrderFailure(
                    order.getUserId(),
                    order.getId(),
                    order.getCreditId(),
                    "Settlement transaction failed on Blockchain. Funds unlocked." + reason
            );

        });
    }

    private void updateOrderInMap(Map<String, Order> map, String orderId, int amountMatched) {
        // 1. Nếu orderId đã có trong map (tức là đã khớp 1 lần trước đó trong batch này), lấy từ map ra
        // 2. Nếu chưa có, lấy từ DB
        Order order = map.computeIfAbsent(orderId, id ->
                orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order not found: " + id))
        );

        // 3. Logic trừ số dư còn lại (remainingAmount)
        int newRemaining = order.getRemainingAmount() - amountMatched;
        order.setRemainingAmount(Math.max(0, newRemaining)); // Đảm bảo không âm

        // 4. Logic set status
        if (order.getRemainingAmount() <= 0) {
            order.setStatus("SETTLED"); // Đã khớp hết
        } else {
            order.setStatus("PARTIAL"); // Khớp một phần (Vẫn còn nằm trên sàn)
        }

        order.setUpdatedAt(LocalDateTime.now());
    }
}
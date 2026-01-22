package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.BlockchainEventDTO;
import com.example.carbon_credit.DTO.MyCreditResponse;
import com.example.carbon_credit.DTO.MyNativeResponse;
import com.example.carbon_credit.Entity.*;
import com.example.carbon_credit.Repository.*;
import com.example.carbon_credit.Util.BlockchainHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletCreditRepository walletCreditRepository;
    private final WalletRepository walletRepository;
    private final CarbonCreditRepository carbonCreditRepository;
    private final OrderRepository orderRepository;
    private final CreditHistoryRepository creditHistoryRepository;
    private final ProcessedTransactionRepository processedTransactionRepository;
    private final WsService wsService;
    private final ConcurrentHashMap<String, Object> walletLocks = new ConcurrentHashMap<>();

    public List<MyCreditResponse> getMyCredits(String walletAddress) {
        return walletCreditRepository.findMyCredits(walletAddress);
    }

    public Optional<MyNativeResponse> getMyNatives(String walletAddress) {
        return walletRepository.findBalanceByAddress(walletAddress);
    }


    @Transactional
    public void handleNativeDeposited(BlockchainEventDTO event) {
        try {

            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
                log.warn("⚠️ Transaction {} already processed. Skipping.", event.getTransactionHash());
                return;
            }

            String userAddress = BlockchainHelper.extractAddressFromTopic(event, 1);
            BigInteger amount = BlockchainHelper.extractUint256FromData(event, 0);

            if (userAddress == null || amount == null) {
                log.error("❌ Invalid NATIVE_DEPOSITED event data");
                return;
            }

            Object lock = walletLocks.computeIfAbsent(userAddress.toLowerCase(), k -> new Object());

            synchronized (lock) {
                log.info("💰 Native Deposited: {} deposited {} wei", userAddress, amount);

                Wallet wallet = walletRepository.findByAddress(userAddress).orElseGet(() -> {
                    log.info("🆕 Creating new wallet for: {}", userAddress);
                    Wallet newWallet = new Wallet();
                    newWallet.setId(UUID.randomUUID().toString());
                    newWallet.setAddress(userAddress);
                    newWallet.setNativeBalance(BigDecimal.ZERO);
                    newWallet.setUpdatedAt(LocalDateTime.now());
                    return walletRepository.save(newWallet);
                });

                BigDecimal currentBalance = wallet.getNativeBalance();
                BigDecimal amountInEther = new BigDecimal(amount).divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP);
                BigDecimal newBalance = currentBalance.add(amountInEther);

                wallet.setNativeBalance(newBalance);
                wallet.setUpdatedAt(LocalDateTime.now());
                walletRepository.save(wallet);

                wsService.notify(
                        "Nạp tiền thành công! ✅",
                        "Bạn vừa nạp " + amountInEther + " ETH vào sàn giao dịch.",
                        "SUCCESS", null, userAddress
                );

                ProcessedTransaction processedTx = ProcessedTransaction.builder()
                        .txHash(event.getTransactionHash())
                        .eventType(event.getEventType())
                        .processedAt(LocalDateTime.now())
                        .build();
                processedTransactionRepository.save(processedTx);

                log.info("✅ Updated wallet {} | Old balance: {} POL | New balance: {} POL", userAddress, currentBalance, newBalance);
            }

        } catch (Exception e) {
            log.error("❌ Error handling NATIVE_DEPOSITED: {}", e.getMessage(), e);
            throw e;
        }

    }

    @Transactional
    public void handleNativeWithdraw(BlockchainEventDTO event) {
        try {

            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
                log.warn("⚠️ Transaction {} already processed. Skipping.", event.getTransactionHash());
                return;
            }

            String userAddress = BlockchainHelper.extractAddressFromTopic(event, 1);
            BigInteger amount = BlockchainHelper.extractUint256FromData(event, 0);

            if (userAddress == null || amount == null) {
                log.error("❌ Invalid NATIVE_WITHDRAWN event data");
                return;
            }

            Object lock = walletLocks.computeIfAbsent(userAddress.toLowerCase(), k -> new Object());

            synchronized (lock) {
                log.info("💸 Native Withdrawn: {} withdrew {} wei", userAddress, amount);

                Wallet wallet = walletRepository.findByAddress(userAddress).orElseThrow(() -> new RuntimeException("Wallet not found: " + userAddress));

                BigDecimal currentBalance = wallet.getNativeBalance();
                BigDecimal amountInEther = new BigDecimal(amount).divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP);
                BigDecimal newBalance = currentBalance.subtract(amountInEther);

                if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                    log.error("❌ Insufficient balance for withdrawal");
                    throw new RuntimeException("Insufficient balance");
                }

                wallet.setNativeBalance(newBalance);
                wallet.setUpdatedAt(LocalDateTime.now());
                walletRepository.save(wallet);

                ProcessedTransaction processedTx = ProcessedTransaction.builder()
                        .txHash(event.getTransactionHash())
                        .eventType(event.getEventType())
                        .processedAt(LocalDateTime.now())
                        .build();
                processedTransactionRepository.save(processedTx);

                wsService.notify(
                        "Rút tiền thành công! ✅",
                        "Bạn vừa rút " + amountInEther + " ETH vào sàn giao dịch.",
                        "SUCCESS", null, userAddress
                );

                log.info("✅ Updated wallet {} | New balance: {} POL", userAddress, newBalance);
            }

        } catch (Exception e) {
            log.error("❌ Error handling NATIVE_WITHDRAWN: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void handleCreditDeposited(BlockchainEventDTO event) {
        try {
            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
                log.warn("⚠️ Transaction {} already processed. Skipping.", event.getTransactionHash());
                return;
            }

            String userAddress = BlockchainHelper.extractAddressFromTopic(event, 1);
            BigInteger creditTokenId = BlockchainHelper.extractUint256FromTopic(event, 2);
            BigInteger amount = BlockchainHelper.extractUint256FromData(event, 0);

            if (userAddress == null || creditTokenId == null || amount == null) {
                log.error("❌ Invalid CREDIT_DEPOSIT event data");
                log.error("   userAddress: {}", userAddress);
                log.error("   creditTokenId: {}", creditTokenId);
                log.error("   amount: {}", amount);
                log.error("   topics: {}", event.getTopics());
                log.error("   data: {}", event.getData());
                return;
            }

            Object lock = walletLocks.computeIfAbsent(userAddress.toLowerCase(), k -> new Object());

            synchronized (lock) {
                log.info("🪙 Credit Deposited: {} deposited {} units of token {}", userAddress, amount, creditTokenId);
                Wallet wallet = walletRepository.findByAddress(userAddress).orElseGet(() -> {
                    log.info("🆕 Creating new wallet for: {}", userAddress);
                    Wallet newWallet = new Wallet();
                    newWallet.setId(UUID.randomUUID().toString());
                    newWallet.setAddress(userAddress);
                    newWallet.setNativeBalance(BigDecimal.ZERO);
                    newWallet.setUpdatedAt(LocalDateTime.now());
                    return walletRepository.save(newWallet);
                });

                CarbonCredit carbonCredit = carbonCreditRepository.findByTokenId(creditTokenId.longValue()).orElseThrow(() -> new RuntimeException("CarbonCredit not found for tokenId: " + creditTokenId));

                WalletCredit walletCredit = walletCreditRepository.findByWalletAddressAndTokenId(userAddress, creditTokenId.longValue()).orElseGet(() -> {
                    log.info("🆕 Creating new credit balance: address={}, tokenId={}", userAddress, creditTokenId);

                    WalletCredit newCredit = new WalletCredit();
                    newCredit.setId(UUID.randomUUID().toString());
                    newCredit.setWallet(wallet);
                    newCredit.setCarbonCredit(carbonCredit);
                    newCredit.setAvailableBalance(BigInteger.ZERO);
                    newCredit.setLockedBalance(BigInteger.ZERO);
                    newCredit.setCreatedAt(LocalDateTime.now());

                    return walletCreditRepository.save(newCredit);
                });

                BigInteger currentBalance = walletCredit.getAvailableBalance();
                BigInteger newBalance = currentBalance.add(amount);

                walletCredit.setAvailableBalance(newBalance);
                walletCredit.setUpdatedAt(LocalDateTime.now());
                walletCreditRepository.save(walletCredit);

                ProcessedTransaction processedTx = ProcessedTransaction.builder()
                        .txHash(event.getTransactionHash())
                        .eventType(event.getEventType())
                        .processedAt(LocalDateTime.now())
                        .build();
                processedTransactionRepository.save(processedTx);

                wsService.notify(
                        "Nạp Credit thành công! ✅",
                        "Bạn vừa nạp " + amount + " credit vào sàn giao dịch.",
                        "SUCCESS", null, userAddress
                );

                log.info("✅ Updated credit | Address: {} | Token: {} | Balance: {}", userAddress, creditTokenId, newBalance);
            }

        } catch (Exception e) {
            log.error("❌ Error handling CREDIT_DEPOSIT: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void handleCreditWithdraw(BlockchainEventDTO event) {
        try {
            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
                log.warn("⚠️ Transaction {} already processed. Skipping.", event.getTransactionHash());
                return;
            }

            String userAddress = BlockchainHelper.extractAddressFromTopic(event, 1);
            BigInteger creditTokenId = BlockchainHelper.extractUint256FromTopic(event, 2);
            BigInteger amount = BlockchainHelper.extractUint256FromData(event, 0);

            if (userAddress == null || creditTokenId == null || amount == null) {
                log.error("❌ Invalid CREDIT_WITHDRAW event data");
                return;
            }

            Object lock = walletLocks.computeIfAbsent(userAddress.toLowerCase(), k -> new Object());

            synchronized (lock) {
                log.info("🪙 Credit Withdrawn: {} withdrew {} units of token {}", userAddress, amount, creditTokenId);

                // ✅ Find by wallet address and token ID
                WalletCredit walletCredit = walletCreditRepository.findByWalletAddressAndTokenId(userAddress, creditTokenId.longValue()).orElseThrow(() -> new RuntimeException("Credit balance not found for address: " + userAddress));

                BigInteger currentBalance = walletCredit.getAvailableBalance();
                BigInteger newBalance = currentBalance.subtract(amount);

                if (newBalance.compareTo(BigInteger.ZERO) < 0) {
                    log.error("❌ Insufficient credit balance");
                    throw new RuntimeException("Insufficient credit balance");
                }

                walletCredit.setAvailableBalance(newBalance);
                walletCredit.setUpdatedAt(LocalDateTime.now());
                walletCreditRepository.save(walletCredit);

                ProcessedTransaction processedTx = ProcessedTransaction.builder()
                        .txHash(event.getTransactionHash())
                        .eventType(event.getEventType())
                        .processedAt(LocalDateTime.now())
                        .build();
                processedTransactionRepository.save(processedTx);

                log.info("✅ Updated credit | Address: {} | Token: {} | Balance: {}", userAddress, creditTokenId, newBalance);

                wsService.notify(
                        "Rút credit thành công! ✅",
                        "Bạn vừa rút " + amount + " credit từ sàn giao dịch.",
                        "SUCCESS", null, userAddress
                );
            }

        } catch (Exception e) {
            log.error("❌ Error handling CREDIT_WITHDRAW: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void handleBalanceLocked(BlockchainEventDTO event) {
        try {

            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
                log.warn("⚠️ Transaction {} already processed. Skipping.", event.getTransactionHash());
                return;
            }

            List<TypeReference<Type>> params = Arrays.asList(
                    (TypeReference) new TypeReference<Utf8String>() {}, // 0. orderId
                    (TypeReference) new TypeReference<Address>() {},    // 1. user
                    (TypeReference) new TypeReference<Uint256>() {},    // 2. amount
                    (TypeReference) new TypeReference<Bool>() {}        // 3. isCreditToken
            );

            List<Type> decoded = BlockchainHelper.decodeAnyData(event.getData(), params);

            if (decoded == null || decoded.size() < 3) {
                log.error("❌ Failed to decode BALANCE_LOCKED data");
                return;
            }

            String orderId = (String) decoded.get(0).getValue();
            String userAddress = (String) decoded.get(1).getValue(); // Lấy User từ Data
            BigInteger amount = (BigInteger) decoded.get(2).getValue();
            boolean isCreditTokenEvent = (Boolean) decoded.get(3).getValue();

            log.info("🔍 Decoded: OrderID={}, User={}, Amount={}", orderId, userAddress, amount);

            if (userAddress == null || orderId == null || amount == null) {
                log.error("❌ Invalid BALANCE_LOCKED event data");
                return;
            }

            // 2. Tìm Order để double-check
            Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found for id: " + orderId));

            Object lock = walletLocks.computeIfAbsent(userAddress.toLowerCase(), k -> new Object());

            synchronized (lock) {
                // Ưu tiên dùng logic từ DB Order Type để quyết định khóa cái gì
                // (Vì Event có thể bị spoof, nhưng DB của mình thì chuẩn)
                boolean isSellOrder = "SELL".equalsIgnoreCase(order.getOrderType());

                if (isSellOrder || isCreditTokenEvent) {
                    // --- KHÓA TÍN CHỈ (CREDIT) ---
                    log.info("🔒 Locking Credit for Order {}", orderId);

                    // Parse Credit ID từ Order (String -> Long)
                    Long creditTokenId = Long.parseLong(order.getCreditId());

                    WalletCredit walletCredit = walletCreditRepository.findByWalletAddressAndTokenId(userAddress, creditTokenId).orElseThrow(() -> new RuntimeException("Credit balance not found"));

                    BigInteger available = walletCredit.getAvailableBalance();
                    BigInteger locked = walletCredit.getLockedBalance();

                    if (available.compareTo(amount) < 0) {
                        throw new RuntimeException("Insufficient available credit balance to lock");
                    }

                    walletCredit.setAvailableBalance(available.subtract(amount));
                    walletCredit.setLockedBalance(locked.add(amount));
                    walletCredit.setUpdatedAt(LocalDateTime.now());
                    walletCreditRepository.save(walletCredit);

                } else {
                    // --- KHÓA TIỀN (NATIVE/USDC) ---
                    log.info("🔒 Locking Native Balance for Order {}", orderId);

                    Wallet wallet = walletRepository.findByAddress(userAddress).orElseThrow(() -> new RuntimeException("Wallet not found"));

                    // Convert Wei -> BigDecimal (giả sử DB lưu 18 decimals)
                    BigDecimal lockAmount = new BigDecimal(amount).divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP);

                    if (wallet.getNativeBalance().compareTo(lockAmount) < 0) {
                        throw new RuntimeException("Insufficient native balance");
                    }

                    // Trừ Available (và cộng Locked nếu có cột đó)
                    wallet.setNativeBalance(wallet.getNativeBalance().subtract(lockAmount));
                    wallet.setNativeLocked(lockAmount);
                    wallet.setUpdatedAt(LocalDateTime.now());
                    walletRepository.save(wallet);
                }

                ProcessedTransaction processedTx = ProcessedTransaction.builder()
                        .txHash(event.getTransactionHash())
                        .eventType(event.getEventType())
                        .processedAt(LocalDateTime.now())
                        .build();
                processedTransactionRepository.save(processedTx);

                log.info("✅ Balance Locked Successfully: {} | Amount: {}", userAddress, amount);
            }

        } catch (Exception e) {
            log.error("❌ Error handling BALANCE_LOCKED: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void handleBalanceUnlocked(BlockchainEventDTO event) {
        try {

            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
                log.warn("⚠️ Transaction {} already processed. Skipping.", event.getTransactionHash());
                return;
            }

            List<TypeReference<Type>> params = Arrays.asList(
                    (TypeReference) new TypeReference<Utf8String>() {},
                    (TypeReference) new TypeReference<Address>() {},
                    (TypeReference) new TypeReference<Uint256>() {}
            );

            List<Type> decoded = BlockchainHelper.decodeAnyData(event.getData(), params);

            if (decoded == null || decoded.size() < 3) {
                log.error("❌ Failed to decode BALANCE_LOCKED data");
                return;
            }

            String orderId = (String) decoded.get(0).getValue();
            String userAddress = (String) decoded.get(1).getValue();
            BigInteger amount = (BigInteger) decoded.get(2).getValue();

            if (userAddress == null || orderId == null || amount == null) {
                log.error("❌ Invalid BALANCE_UNLOCKED event data");
                return;
            }

            // --- BƯỚC QUAN TRỌNG: TÌM ORDER ĐỂ BIẾT LOẠI TÀI SẢN ---
            Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

            Object lock = walletLocks.computeIfAbsent(userAddress.toLowerCase(), k -> new Object());

            synchronized (lock) {
                // Dựa vào loại lệnh (BUY/SELL) để biết cần mở khóa ví nào
                if ("SELL".equalsIgnoreCase(order.getOrderType())) {
                    // SELL Order = Đã khóa Tín chỉ -> Giờ mở khóa Tín chỉ
                    log.info("🔓 Unlocking Credit for Order {}", orderId);

                    Long creditTokenId = Long.parseLong(order.getCreditId());

                    WalletCredit walletCredit = walletCreditRepository.findByWalletAddressAndTokenId(userAddress, creditTokenId).orElseThrow(() -> new RuntimeException("Credit balance not found"));

                    BigInteger available = walletCredit.getAvailableBalance();
                    BigInteger locked = walletCredit.getLockedBalance();

                    if (locked.compareTo(amount) < 0) {
                        log.warn("⚠️ Locked credit {} < Unlock amount {}", locked, amount);
                    }

                    walletCredit.setAvailableBalance(available.add(amount));
                    // Đảm bảo không âm
                    walletCredit.setLockedBalance(locked.subtract(amount).max(BigInteger.ZERO));
                    walletCredit.setUpdatedAt(LocalDateTime.now());
                    walletCreditRepository.save(walletCredit);

                } else {
                    // BUY Order = Đã khóa Tiền -> Giờ mở khóa Tiền
                    log.info("🔓 Unlocking Native Balance for Order {}", orderId);

                    Wallet wallet = walletRepository.findByAddress(userAddress).orElseThrow(() -> new RuntimeException("Wallet not found"));

                    BigDecimal unlockAmount = new BigDecimal(amount).divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP);

                    // Cộng lại vào ví Native
                    wallet.setNativeBalance(wallet.getNativeBalance().add(unlockAmount));
                    wallet.setNativeLocked(wallet.getNativeLocked().subtract(unlockAmount));
                    wallet.setUpdatedAt(LocalDateTime.now());
                    walletRepository.save(wallet);
                }

                ProcessedTransaction processedTx = ProcessedTransaction.builder()
                        .txHash(event.getTransactionHash())
                        .eventType(event.getEventType())
                        .processedAt(LocalDateTime.now())
                        .build();
                processedTransactionRepository.save(processedTx);

                log.info("✅ Balance Unlocked Successfully");
            }

        } catch (Exception e) {
            log.error("❌ Error handling BALANCE_UNLOCKED: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void handleTradeSettled(BlockchainEventDTO event) {
        try {
            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
                log.warn("⚠️ Transaction {} already processed. Skipping.", event.getTransactionHash());
                return;
            }

            String buyerAddress = BlockchainHelper.extractAddressFromTopic(event, 2);
            String sellerAddress = BlockchainHelper.extractAddressFromTopic(event, 3);

            List<TypeReference<Type>> params = Arrays.asList(
                    (TypeReference) new TypeReference<Uint256>() {},
                    (TypeReference) new TypeReference<Uint256>() {},
                    (TypeReference) new TypeReference<Uint256>() {}
            );


            // Data: [0:TokenId, 1:Amount, 2:TotalValue]
            List<Type> decodedData = BlockchainHelper.decodeAnyData(event.getData(), params);

            if (decodedData == null || decodedData.size() < 3) {
                log.error("❌ Failed to decode TRADE_SETTLED data");
                return;
            }


            BigInteger creditTokenId = (BigInteger) decodedData.get(0).getValue();
            BigInteger creditAmount = (BigInteger) decodedData.get(1).getValue();
            BigInteger totalValueWei = (BigInteger) decodedData.get(2).getValue();

            if (buyerAddress == null || sellerAddress == null) {
                log.error("❌ Invalid TRADE_SETTLED address data");
                return;
            }

            log.info("⚖️ Settling Trade: Buyer={} | Seller={} | Token={} | Amount={} | Val={}", buyerAddress, sellerAddress, creditTokenId, creditAmount, totalValueWei);

            CarbonCredit carbonCredit = carbonCreditRepository.findByTokenId(creditTokenId.longValue())
                    .orElseThrow(() -> new RuntimeException("Carbon Credit not found for token ID: " + creditTokenId));

            // 2. Cập nhật cho NGƯỜI MUA (Buyer)
            Object buyerLock = walletLocks.computeIfAbsent(buyerAddress.toLowerCase(), k -> new Object());
            synchronized (buyerLock) {
                // A. Cộng Tín chỉ vào ví (Available)
                WalletCredit buyerCredit = walletCreditRepository.findByWalletAddressAndTokenId(buyerAddress, creditTokenId.longValue())
                        .orElseGet(() -> {
                            Wallet wallet = walletRepository.findByAddress(buyerAddress)
                                    .orElseThrow(() -> new RuntimeException("Wallet not found: " + buyerAddress));

                            return walletCreditRepository.save(WalletCredit.builder()
                                    .id(UUID.randomUUID().toString())
                                    .wallet(wallet)
                                    .carbonCredit(carbonCredit)
                                    .availableBalance(BigInteger.ZERO)
                                    .lockedBalance(BigInteger.ZERO)
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build());
                        });

                buyerCredit.setAvailableBalance(buyerCredit.getAvailableBalance().add(creditAmount));
                walletCreditRepository.save(buyerCredit);

                // B. Trừ tiền Native đã khóa (Locked)
                Wallet buyerWallet = walletRepository.findByAddress(buyerAddress).orElseThrow(() -> new RuntimeException("Buyer wallet not found"));

                BigDecimal valueDecimal = new BigDecimal(totalValueWei).divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP);

                // Giả định Entity Wallet có field nativeLocked (như bạn dùng trong handleBalanceLocked)
                // Nếu chưa có, bạn cần đảm bảo DB có cột này
                BigDecimal currentLocked = buyerWallet.getNativeLocked() != null ? buyerWallet.getNativeLocked() : BigDecimal.ZERO;
                buyerWallet.setNativeLocked(currentLocked.subtract(valueDecimal).max(BigDecimal.ZERO));

                walletRepository.save(buyerWallet);
            }

            // 3. Cập nhật cho NGƯỜI BÁN (Seller)
            Object sellerLock = walletLocks.computeIfAbsent(sellerAddress.toLowerCase(), k -> new Object());
            synchronized (sellerLock) {
                // A. Cộng tiền Native vào ví (Available)
                Wallet sellerWallet = walletRepository.findByAddress(sellerAddress).orElseThrow(() -> new RuntimeException("Seller wallet not found"));

                BigDecimal valueDecimal = new BigDecimal(totalValueWei).divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.HALF_UP);

                sellerWallet.setNativeBalance(sellerWallet.getNativeBalance().add(valueDecimal));
                walletRepository.save(sellerWallet);

                // B. Trừ Tín chỉ đã khóa (Locked)
                WalletCredit sellerCredit = walletCreditRepository.findByWalletAddressAndTokenId(sellerAddress, creditTokenId.longValue()).orElseThrow(() -> new RuntimeException("Seller credit wallet not found"));

                BigInteger currentLocked = sellerCredit.getLockedBalance();
                sellerCredit.setLockedBalance(currentLocked.subtract(creditAmount).max(BigInteger.ZERO));
                walletCreditRepository.save(sellerCredit);
            }

            // 4. LƯU LỊCH SỬ GIAO DỊCH (CREDIT HISTORY)
            CreditHistory history = CreditHistory.builder()
                    .id(UUID.randomUUID().toString())
                    .creditId(carbonCredit.getId())
                    .sellerId(sellerAddress)
                    .buyerId(buyerAddress)
                    .amount(creditAmount.intValue())
                    .txHash(event.getTransactionHash())
                    .tradeAt(LocalDateTime.now())
                    .build();

            creditHistoryRepository.save(history);

            ProcessedTransaction processedTx = ProcessedTransaction.builder()
                    .txHash(event.getTransactionHash())
                    .eventType(event.getEventType())
                    .processedAt(LocalDateTime.now())
                    .build();
            processedTransactionRepository.save(processedTx);

            log.info("✅ Trade Settled & History Saved Successfully");

            try {
                // Tính toán giá trị ETH để hiển thị trong tin nhắn
                BigDecimal valueInEth = new BigDecimal(totalValueWei)
                        .divide(new BigDecimal("1000000000000000000"), 6, RoundingMode.HALF_UP);

                // --- THÔNG BÁO CHO NGƯỜI MUA ---
                wsService.notify(
                        "Khớp lệnh mua thành công!",
                        String.format("Bạn đã nhận được %s tín chỉ carbon. Tổng chi phí: %s ETH.",
                                creditAmount, valueInEth.stripTrailingZeros().toPlainString()),
                        "SUCCESS",
                        null,
                        buyerAddress
                );

                // --- THÔNG BÁO CHO NGƯỜI BÁN ---
                wsService.notify(
                        "Lệnh bán đã khớp! 💰",
                        String.format("Bạn đã bán thành công %s tín chỉ carbon. Tài khoản đã cộng: %s ETH.",
                                creditAmount, valueInEth.stripTrailingZeros().toPlainString()),
                        "SUCCESS",
                        null,
                        sellerAddress
                );

                log.info("🔔 Sent trade settlement notifications to Buyer and Seller.");
            } catch (Exception notifyEx) {
                // Log lỗi thông báo nhưng không làm rollback giao dịch tiền tệ
                log.warn("⚠️ Could not send trade notifications: {}", notifyEx.getMessage());
            }

        } catch (Exception e) {
            log.error("❌ Error handling TRADE_SETTLED: {}", e.getMessage(), e);
            throw e;
        }
    }

}

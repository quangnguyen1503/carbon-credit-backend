package com.example.carbon_credit.Service;

import com.example.carbon_credit.Entity.BlockchainTransaction;
import com.example.carbon_credit.Entity.CarbonCredit;
import com.example.carbon_credit.Entity.Wallet;
import com.example.carbon_credit.Entity.WalletCredit;
import com.example.carbon_credit.Repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletCreditRepository walletCreditRepository;
    private final BlockchainTransactionRepository transactionRepository;
    private final CarbonCreditRepository carbonCreditRepository;
    private final WsService wsService; // Inject WsService vào

    @Transactional
    public void processNativeDeposit(String address, BigInteger amount, String txHash) {
        try {
            log.info("🛠 Đang xử lý nạp tiền cho ví: {}", address);

            // 1. Đảm bảo địa chỉ luôn viết thường
            String cleanAddress = address.toLowerCase().trim();

            // 2. Tìm ví (Nếu chưa có ví trong bảng wallets thì phải tạo)
            Wallet wallet = walletRepository.findByAddress(cleanAddress)
                    .orElseGet(() -> {
                        log.info("🆕 Chưa có bản ghi ví, đang tạo mới cho: {}", cleanAddress);
                        Wallet newWallet = Wallet.builder()
                                .id(UUID.randomUUID().toString())
                                .address(cleanAddress)
                                .nativeBalance(BigDecimal.ZERO)
                                .nativeLocked(BigDecimal.ZERO)
                                .updatedAt(LocalDateTime.now())
                                .build();
                        return walletRepository.save(newWallet);
                    });

            // 3. Kiểm tra xem giao dịch này đã lưu chưa
            if (transactionRepository.existsById(txHash)) {
                log.warn("⚠️ Giao dịch {} đã được xử lý trước đó, bỏ qua.", txHash);
                return;
            }

            // 4. Cộng tiền
            BigDecimal depositAmount = new BigDecimal(amount);
            wallet.setNativeBalance(wallet.getNativeBalance().add(depositAmount));
            wallet.setUpdatedAt(LocalDateTime.now());
            walletRepository.save(wallet);

            // 5. Lưu lịch sử (Chỗ này dễ lỗi nhất nếu thiếu User)
            BlockchainTransaction tx = BlockchainTransaction.builder()
                    .txHash(txHash)
                    .walletAddress(cleanAddress) // Đảm bảo cột này trong DB trùng với address ví
                    .type("DEPOSIT_NATIVE")
                    .amount(depositAmount)
                    .status("SUCCESS")
                    .blockNumber(0L) // Có thể lấy blockNumber từ log nếu cần
                    .createdAt(LocalDateTime.now())
                    .build();

            transactionRepository.save(tx);

            log.info("✅ ĐÃ LƯU DATABASE THÀNH CÔNG: +{} Wei cho ví {}", amount, cleanAddress);

        } catch (Exception e) {
            log.error("❌ LỖI DATABASE KHI NẠP TIỀN: {}", e.getMessage());
            e.printStackTrace(); // In toàn bộ lỗi ra để soi dòng nào sai
            throw e; // Ném ngược ra để Spring biết đường Rollback
        }
    }

    @Transactional
    public void processCreditDeposit(String address, BigInteger tokenId, BigInteger amount, String txHash) {
        if (transactionRepository.existsById(txHash)) return;

        // 1. Đảm bảo ví tồn tại
        Wallet wallet = walletRepository.findByAddress(address)
                .orElseGet(() -> createNewWallet(address));

        // 2. Đảm bảo CarbonCredit tồn tại trong danh mục hệ thống
        CarbonCredit carbonCredit = carbonCreditRepository.findByTokenId(tokenId)
                .orElseThrow(() -> new RuntimeException("Loại Credit chưa được niêm yết: " + tokenId));

        // 3. Tìm bản ghi số dư Credit (Dùng đúng tên phương thức trong Repository)
        WalletCredit credit = walletCreditRepository.findByWalletAddressAndTokenId(address, tokenId)
                .orElseGet(() -> createNewWalletCredit(wallet, carbonCredit));

        // 4. Cộng số dư (Long dùng toán tử +)
        credit.setAvailableBalance(credit.getAvailableBalance() + amount.longValue());
        credit.setUpdatedAt(LocalDateTime.now());
        walletCreditRepository.save(credit);

        saveTransaction(txHash, address, "DEPOSIT_CREDIT", tokenId, new BigDecimal(amount));
        wsService.broadcastBalanceUpdate(address, "CREDIT", credit);
    }

    // --- CÁC HELPER METHOD ĐỂ FIX LỖI "Cannot resolve method" ---

    private Wallet createNewWallet(String address) {
        return Wallet.builder()
                .id(UUID.randomUUID().toString())
                .address(address)
                .nativeBalance(BigDecimal.ZERO)
                .nativeLocked(BigDecimal.ZERO)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private WalletCredit createNewWalletCredit(Wallet wallet, CarbonCredit carbonCredit) {
        return WalletCredit.builder()
                .id(UUID.randomUUID().toString())
                .wallet(wallet)
                .carbonCredit(carbonCredit)
                .availableBalance(0L)
                .lockedBalance(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void saveTransaction(String txHash, String address, String type, BigInteger tokenId, BigDecimal amount) {
        BlockchainTransaction tx = BlockchainTransaction.builder()
                .txHash(txHash)
                .walletAddress(address)
                .type(type)
                .tokenId(tokenId)
                .amount(amount)
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();
        transactionRepository.save(tx);
    }
    @Transactional
    public void processNativeWithdrawFinalize(String address, BigInteger amount, String txHash) {
        // 1. Kiểm tra txHash để tránh xử lý trùng (Idempotency)
        if (transactionRepository.existsById(txHash)) return;

        // 2. Tìm ví của User
        Wallet wallet = walletRepository.findByAddress(address)
                .orElseThrow(() -> new RuntimeException("Wallet not found for address: " + address));

        // 3. Cập nhật số dư
        BigDecimal withdrawAmount = new BigDecimal(amount);

        // LOGIC: Thông thường khi rút tiền, tiền đã bị trừ ở 'availableBalance'
        // và chuyển sang 'lockedBalance' lúc User đặt lệnh rút.
        // Khi Blockchain xác nhận thành công, chúng ta trừ ở 'lockedBalance'.

        if (wallet.getNativeLocked().compareTo(withdrawAmount) >= 0) {
            wallet.setNativeLocked(wallet.getNativeLocked().subtract(withdrawAmount));
        } else {
            // Trường hợp User tự rút trực tiếp từ Smart Contract mà không qua API Backend
            wallet.setNativeBalance(wallet.getNativeBalance().subtract(withdrawAmount));
        }

        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        // 4. Lưu lịch sử giao dịch
        BlockchainTransaction tx = BlockchainTransaction.builder()
                .txHash(txHash)
                .walletAddress(address)
                .type("WITHDRAW_NATIVE")
                .amount(withdrawAmount)
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();
        transactionRepository.save(tx);

        log.info("💸 Withdraw Finalized: Wallet {} | Amount {}", address, withdrawAmount);

        // 5. (Tùy chọn) Gửi thông báo WebSocket cho FE
        wsService.broadcastBalanceUpdate(address, "NATIVE", wallet.getNativeBalance());
    }
}
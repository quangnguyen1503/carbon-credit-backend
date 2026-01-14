package com.example.carbon_credit.Service;

import com.example.carbon_credit.contract.CarbonCreditExchange;
import com.example.carbon_credit.DTO.TradeDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractService {

    private final Web3j web3j;
    private final Credentials credentials;

    @Value("${web3.contractAddress}")
    private String contractAddress;

    private CarbonCreditExchange contract;

    // Cấu hình Gas: Tùy chỉnh theo mạng (ví dụ Polygon Amoy cần Gas Price cao hơn)
    private static final BigInteger GAS_PRICE = BigInteger.valueOf(30_000_000_000L); // 30 Gwei
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(1_000_000L); // Batch cần limit cao hơn

    @PostConstruct
    public void init() {
        if (contractAddress == null || contractAddress.isEmpty()) {
            log.error("❌ web3.contractAddress chưa được cấu hình!");
            return;
        }
        try {
            // QUAN TRỌNG: Chuyển địa chỉ về lowercase để tránh Web3j hiểu nhầm là ENS name
            String cleanAddress = contractAddress.trim().toLowerCase();

            // Đảm bảo có tiền tố 0x
            if (!cleanAddress.startsWith("0x")) cleanAddress = "0x" + cleanAddress;

            contract = CarbonCreditExchange.load(
                    cleanAddress,
                    web3j,
                    credentials,
                    new StaticGasProvider(GAS_PRICE, GAS_LIMIT)
            );
            log.info("✅ Contract loaded successfully at: {}", cleanAddress);
        } catch (Exception e) {
            log.error("❌ Lỗi khởi tạo Contract: {}", e.getMessage());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 1. TRUY VẤN (READ)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public BigInteger getNativeBalance(String userAddress) throws Exception {
        return contract.getNativeBalance(userAddress).send();
    }

    public BigInteger getCreditBalance(BigInteger tokenId, String userAddress) throws Exception {
        return contract.getCreditBalance(tokenId, userAddress).send();
    }

    public String testRpcConnection() {
        try {
            String version = web3j.web3ClientVersion().send().getWeb3ClientVersion();
            return "Connected! Version: " + version;
        } catch (Exception e) {
            return "Connection failed: " + e.getMessage();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 2. QUẢN LÝ LỆNH (LOCK/UNLOCK)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public TransactionReceipt lockBalance(String orderId, String user, BigInteger tokenId, BigInteger amount, boolean isCreditToken) throws Exception {
        log.info("🔒 Executing lockBalance On-chain: Order={}", orderId);
        byte[] orderIdBytes = convertToBytes32(orderId);
        return contract.lockBalance(orderIdBytes, user, tokenId, amount, isCreditToken).send();
    }

    public TransactionReceipt unlockBalance(String orderId) throws Exception {
        log.info("🔓 Executing unlockBalance On-chain: Order={}", orderId);
        byte[] orderIdBytes = convertToBytes32(orderId);
        return contract.unlockBalance(orderIdBytes).send();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 3. QUYẾT TOÁN (SETTLEMENT)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Quyết toán nhiều giao dịch một lúc (Batch) theo ABI
     */
    public TransactionReceipt processBatchSettlement(
            BigInteger batchId,
            List<TradeDTO> tradesDto,
            List<String> buyOrderIds,
            List<String> sellOrderIds
    ) throws Exception {

        log.info("⛓️ Sending Batch Settlement #{} to Blockchain ({} trades)", batchId, tradesDto.size());

        // Chuyển đổi DTO sang Struct của Wrapper
        List<CarbonCreditExchange.Trade> trades = tradesDto.stream()
                .map(dto -> new CarbonCreditExchange.Trade(
                        dto.getBuyer(),
                        dto.getSeller(),
                        dto.getCreditTokenId(),
                        dto.getAmount(),
                        dto.getTotalValue()
                )).collect(Collectors.toList());

        // Chuyển đổi ID sang bytes32
        List<byte[]> buyIds = buyOrderIds.stream().map(this::convertToBytes32).collect(Collectors.toList());
        List<byte[]> sellIds = sellOrderIds.stream().map(this::convertToBytes32).collect(Collectors.toList());

        return contract.processBatchSettlement(batchId, trades, buyIds, sellIds).send();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 4. NẠP / RÚT (DEPOSIT / WITHDRAW)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public TransactionReceipt withdrawNative(BigInteger amount) throws Exception {
        log.info("💸 Executing withdrawNative: {} Wei", amount);
        return contract.withdrawNative(amount).send();
    }

    // Helper: Convert String UUID sang bytes32
    private byte[] convertToBytes32(String input) {
        byte[] result = new byte[32];
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(inputBytes, 0, result, 0, Math.min(inputBytes.length, 32));
        return result;
    }
}
package com.example.carbon_credit.Listener;

import com.example.carbon_credit.Service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockchainEventListener {

    private final Web3j web3j;
    private final WalletService walletService;

    @Value("${web3.contractAddress}")
    private String contractAddress;

    private BigInteger lastProcessedBlock = BigInteger.ZERO;

    private static final String EVENT_NATIVE_DEPOSITED = "0x785368a044d18084a44f911a3d92244249a888c7512d7c5874415f3ec52f7f98";

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        log.info("🚀 [HỆ THỐNG] Listener Ready. Target: {}", contractAddress);
    }

    @Scheduled(fixedDelay = 3000)
    public void pollBlockchainTask() {
        try {
            BigInteger currentBlock = web3j.ethBlockNumber().send().getBlockNumber();

            if (currentBlock.compareTo(lastProcessedBlock) < 0) {
                lastProcessedBlock = BigInteger.ZERO;
            }

            EthFilter filter = new EthFilter(
                    DefaultBlockParameter.valueOf(lastProcessedBlock),
                    DefaultBlockParameter.valueOf(currentBlock),
                    new ArrayList<String>()
            );

            EthLog response = web3j.ethGetLogs(filter).send();
            if (response.hasError()) return;

            List<EthLog.LogResult> logs = response.getLogs();

            // CHỈ IN LOG KHI CÓ BLOCK MỚI HOẶC CÓ EVENT
            if (logs != null && !logs.isEmpty()) {
                log.info("🔍 Quét Block {} -> {}: Thấy {} logs", lastProcessedBlock, currentBlock, logs.size());

                for (EthLog.LogResult<?> result : logs) {
                    try {
                        Log logObj = (Log) result.get();

                        // Lọc đúng địa chỉ contract
                        if (logObj.getAddress() != null && logObj.getAddress().equalsIgnoreCase(contractAddress.trim())) {
                            handleBlockchainEvent(logObj);
                        }
                    } catch (Exception e) {
                        // Bỏ qua log lỗi để chạy tiếp log sau
                    }
                }
            }

            // LUÔN CẬP NHẬT BLOCK ĐỂ THOÁT VÒNG LẶP
            lastProcessedBlock = currentBlock.add(BigInteger.ONE);

        } catch (Exception e) {
            log.error("❌ Lỗi hệ thống: {}", e.getMessage());
        }
    }

    private void handleBlockchainEvent(Log ethLog) {
        List<String> topics = ethLog.getTopics();
        if (topics == null || topics.isEmpty()) return;

        String topic0 = topics.get(0);

        if (topic0.equalsIgnoreCase(EVENT_NATIVE_DEPOSITED)) {
            try {
                // Kiểm tra xem có đủ topics không (phòng lỗi IndexOutOfBounds)
                if (topics.size() < 2) return;

                String walletAddress = "0x" + topics.get(1).substring(26);
                BigInteger amount = new BigInteger(ethLog.getData().substring(2), 16);
                String txHash = ethLog.getTransactionHash();

                log.info("💰 [NẠP TIỀN] Ví: {} | Tiền: {} Wei", walletAddress, amount);
                walletService.processNativeDeposit(walletAddress, amount, txHash);
            } catch (Exception e) {
                log.error("❌ Lỗi parse nạp tiền: {}", e.getMessage());
            }
        }
    }
}
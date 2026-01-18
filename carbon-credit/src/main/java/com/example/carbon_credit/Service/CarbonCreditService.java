package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.BlockchainEventDTO;
import com.example.carbon_credit.Entity.CarbonCredit;
import com.example.carbon_credit.Entity.MintHistory;
import com.example.carbon_credit.Entity.ProcessedTransaction;
import com.example.carbon_credit.Entity.Project;
import com.example.carbon_credit.Repository.CarbonCreditRepository;
import com.example.carbon_credit.Repository.MintHistoryRepository;
import com.example.carbon_credit.Repository.ProcessedTransactionRepository;
import com.example.carbon_credit.Repository.ProjectRepository;
import com.example.carbon_credit.Util.BlockchainHelper;
import com.example.carbon_credit.constants.ProjectStatus;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class CarbonCreditService {
    @Autowired
    CarbonCreditRepository carbonCreditRepository;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    MintHistoryRepository mintHistoryRepository;

    @Autowired
    private WsService  wsService;

    @Autowired
    private ProcessedTransactionRepository processedTransactionRepository;


    @Transactional
    public CarbonCredit mintCarbonCredit(
            String projectId,
            String userId,
            String mintHash,
            long mintAmount
    ) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        CarbonCredit carbonCredit = carbonCreditRepository
                .findByProjectId(projectId)
                .orElseThrow(() -> new RuntimeException("CarbonCredit not found"));

        long newIssued = carbonCredit.getIssueAmount() + mintAmount;

        if (newIssued > carbonCredit.getTotalAmount()) {
            throw new IllegalStateException("Exceed total credits");
        }

        // 1️⃣ update state
        carbonCredit.setIssueAmount(newIssued);

        // 2️⃣ audit log
        MintHistory history = MintHistory.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .creditId(carbonCredit.getId())
                .userId(userId)
                .mintAmount(mintAmount)
                .txHash(mintHash)
                .createdAt(LocalDateTime.now())
                .build();

        mintHistoryRepository.save(history);

        return carbonCreditRepository.save(carbonCredit);
    }

    @Transactional
    public void handleCreditMinted(BlockchainEventDTO event) {
        try {
            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
                log.warn("⚠️ Transaction {} already processed. Skipping.", event.getTransactionHash());
                return;
            }

            // 1. Lấy dữ liệu từ Topics
            BigInteger creditTokenId = BlockchainHelper.extractUint256FromTopic(event, 1);
            BigInteger nftTokenId = BlockchainHelper.extractUint256FromTopic(event, 2);

            // 2. Định nghĩa params dùng Wildcard <?> để tránh lỗi Incompatible types
            List<TypeReference<Type>> params = Arrays.asList(
                    (TypeReference) new TypeReference<Utf8String>() {},
                    (TypeReference) new TypeReference<Address>() {},
                    (TypeReference) new TypeReference<Uint256>() {},
                    (TypeReference) new TypeReference<Uint256>() {}
            );

            List<Type> decoded = BlockchainHelper.decodeAnyData(event.getData(), params);

            if (decoded != null && decoded.size() >= 4) {
                String projectId = (String) decoded.get(0).getValue();
                String toAddress = (String) decoded.get(1).getValue();
                BigInteger amountBI = (BigInteger) decoded.get(2).getValue();

                log.info("🏭 [SYNC] CreditMinted: Project={}, Amount={}", projectId, amountBI);

                this.mintCarbonCredit(
                        projectId,
                        toAddress.toLowerCase(),
                        event.getTransactionHash(),
                        amountBI.longValue()
                );

                // 5. Lưu vết
                ProcessedTransaction processedTx = ProcessedTransaction.builder()
                        .txHash(event.getTransactionHash())
                        .eventType(event.getEventType())
                        .processedAt(LocalDateTime.now())
                        .build();
                processedTransactionRepository.save(processedTx);

                log.info("MINTED SUSSCESS: {}", processedTx);

                wsService.notify(
                        String.format("Mint Token Thành Công"),  // Title
                        String.format("Bạn đã mint thành công %s credits từ dự án %s. Token ID: Credit=%s, NFT=%s. Số lượng: %s.",
                                amountBI,  // Amount
                                projectId,  // Project
                                creditTokenId,  // Credit Token ID
                                nftTokenId,  // NFT Token ID
                                amountBI),  // Amount lặp lại nếu cần
                        "SUCCESS",  // Type
                        null,       // Role (null để gửi cá nhân)
                        toAddress.toLowerCase()  // Wallet/Recipient
                );
            }


        } catch (Exception e) {
            log.error("❌ Error handling CREDIT_MINTED: {}", e.getMessage());
            throw new RuntimeException("Error syncing Mint event", e);
        }
    }

    public CarbonCredit getCarbonCreditByTokenId(long tokenId) {
        return carbonCreditRepository.findByTokenId(tokenId).orElse(null);
    }
}

package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.*;
import com.example.carbon_credit.DTO.BlockchainEventDTO;
import com.example.carbon_credit.DTO.CertificateDetailResponse;
import com.example.carbon_credit.DTO.CertificateRecordDTO;
import com.example.carbon_credit.DTO.CertificateResponse;
import com.example.carbon_credit.Entity.*;
import com.example.carbon_credit.Repository.*;
import com.example.carbon_credit.Util.BlockchainHelper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class CertificateService {
    private final ConcurrentHashMap<String, Object> walletLocks = new ConcurrentHashMap<>();
    @Autowired
    CertificateRepository certificateRepository;

    @Autowired
    CarbonCreditRepository carbonCreditRepository;
    @Autowired
    ProcessedTransactionRepository processedTransactionRepository;
    @Autowired
    WalletCreditRepository walletCreditRepository;
    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    CertificateRecordRepository certificateRecordRepository;


    @Autowired
    ContractService contractService;

    // @Transactional
    // public CertificateResponse retireMultiToken(String userId, RetireRequestDTO
    // requestDTO) {
    // Certificate certificate = new Certificate();
    // certificate.setId(UUID.randomUUID().toString());
    // certificate.setUserId(userId);
    // certificate.setOnchainTxHash(requestDTO.getOnchainTxHash());
    // certificate.setNftTokenId(requestDTO.getNftTokenId());
    // certificate.setReason(requestDTO.getReason());
    // certificate.setStatus("PENDDING");
    // certificate.setCreatedAt(LocalDateTime.now());
    //
    // int total = 0;
    //
    // for (RetireRequestDTO.RetireRecordDTO r : requestDTO.getRecords()) {
    // CertificateRecord certificateRecord = new CertificateRecord();
    // certificateRecord.setId(UUID.randomUUID().toString());
    // certificateRecord.setTokenId(r.getTokenId());
    // certificateRecord.setAmount(r.getAmount());
    // certificateRecord.setCertificate(certificate);
    //
    // certificate.getRecords().add(certificateRecord);
    //
    // total += r.getAmount();
    // }
    //
    // certificate.setTotalAmount(total);
    // certificateRepository.save(certificate);
    // return new CertificateResponse(certificate.getId(), certificate.getStatus(),
    // certificate.getTotalAmount(), certificate.getCreatedAt());
    // }

    public List<CertificateResponse> getMyCertificates(String userId) {
        return certificateRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(c -> new CertificateResponse(c.getId(), c.getUserId(), c.getTotalAmount(), c.getCreatedAt())).toList();
    }

    public CertificateDetailResponse getDetail(String certId) {
        CertificateDetailResponse detail =
                certificateRepository.findCertificateDetailById(certId)
                        .orElseThrow(() ->
                                new RuntimeException("Certificate not found: " + certId)
                        );

        List<Object[]> rows =
                certificateRepository.findCertificateRecords(certId);

        List<CertificateDetailResponse.RecordDetail> records = rows.stream()
                .map(r -> {
                    CertificateDetailResponse.RecordDetail rd =
                            new CertificateDetailResponse.RecordDetail();
                    rd.setTokenId((BigInteger) r[0]);
                    rd.setAmount((BigInteger) r[1]);
                    rd.setProjectName((String) r[2]);
                    return rd;
                })
                .toList();

        detail.setRecords(records);

        return detail;
    }


    public Page<Certificate> getCertificateWithPaginationAndSort(String status, int pageNumber, int pageSize, String sortBy, String sortDirection) {
        Sort sort = Sort.by(sortBy);
        if ("desc".equalsIgnoreCase(sortDirection)) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }

        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        return certificateRepository.findAll(pageable);

    }

    // @Transactional
    // public Certificate approveCertificate(String CertificateId, String adminId) {
    // Certificate request =
    // certificateRepository.findById(CertificateId).orElseThrow(() -> new
    // RuntimeException("Certificate not found"));
    //
    // if (!request.getStatus().equals("PENDING")) {
    // throw new RuntimeException("Certificate is not pending");
    // }
    //
    // request.setStatus("APPROVED");
    // request.setApprovedBy(adminId);
    // request.setApprovedAt(LocalDateTime.now());
    //
    // return certificateRepository.save(request);
    // }

    // @Transactional
    // public Certificate comfirmOnChain(String CertificateId, String txHash, String
    // nftTokenId) {
    // Certificate request =
    // certificateRepository.findById(CertificateId).orElseThrow(() -> new
    // RuntimeException("Retire request not found"));
    //
    // if (!request.getStatus().equals("APPROVED")) {
    // throw new RuntimeException("Request is not APPROVED");
    // }
    //
    // request.setStatus("ONCHAIN_DONE"); // Sửa tên status cho rõ
    // request.setOnchainTxHash(txHash);
    // request.setNftTokenId(nftTokenId); // Override nếu cần
    // // Không set approvedAt nữa (đã set ở approve)
    //
    // return certificateRepository.save(request);
    // }

    public List<Certificate> getRetireHistory(LocalDate from, LocalDate to) {
        if (to == null || from == null) {
            to = LocalDate.now();
            from = to.minusDays(7);
        }

        return certificateRepository.findByCreatedAtBetween(from.atStartOfDay(), to.atTime(23, 59, 59));

    }

    @Transactional
    public void handleBatchCeritificateRetired(BlockchainEventDTO event) {
        try {
            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
                log.warn(" Transaction {} already processed. Skipping.", event.getTransactionHash());
                return;
            }

            BigInteger certificateTokenId = BlockchainHelper.extractUint256FromTopic(event, 1);
            String retiredBy = BlockchainHelper.extractAddressFromTopic(event, 2);

            if (certificateTokenId == null) {
                log.error(" Invalid Credit Token Id event data");
                return;
            }
            List<TypeReference<Type>> params = Arrays.asList((TypeReference) new TypeReference<Uint256>() {
            }, (TypeReference) new TypeReference<Uint256>() {
            }, (TypeReference) new TypeReference<Uint256>() {
            });

            List<Type> decoded = BlockchainHelper.decodeAnyData(event.getData(), params);

            if (decoded == null || decoded.size() < 3) {
                log.error(" Failed to decode CREDIT_RETIRED data");
                return;
            }

            BigInteger totalValue = (BigInteger) decoded.get(0).getValue();
            BigInteger recordCount = (BigInteger) decoded.get(1).getValue();

            String certificateId = UUID.randomUUID().toString();

            Object lock = walletLocks.computeIfAbsent(certificateId.toLowerCase(), k -> new Object());

            synchronized (lock) {
                log.info(" Certificate: UUID={}, User={}, Amount={}, Record={}", certificateId, retiredBy, totalValue, recordCount);

                Certificate certificate = Certificate.builder().id(certificateId).userId(retiredBy).totalAmount(totalValue).nftTokenId(certificateTokenId).txHash(event.getTransactionHash()).createdAt(LocalDateTime.now()).build();

                certificateRepository.save(certificate);

                getRecordFromChain(certificate);

                ProcessedTransaction processedTx = ProcessedTransaction.builder().txHash(event.getTransactionHash()).eventType(event.getEventType()).processedAt(LocalDateTime.now()).build();
                processedTransactionRepository.save(processedTx);

                log.info(" Certificate created successfull {} by {}! Total Value {}", certificateTokenId, retiredBy, totalValue);
            }

        } catch (Exception e) {
            log.error(" Error handling CREDIT_RETIRED: {}", e.getMessage(), e);
            throw e;
        }
    }



    private void getRecordFromChain(Certificate certificate) {
        try {
            List<CertificateRecordDTO> records = contractService.getCertificateRecords(certificate.getNftTokenId());

            for (CertificateRecordDTO record : records) {
                // 1. Lưu bản ghi chi tiết của chứng chỉ
                CertificateRecord detail = CertificateRecord.builder()
                        .id(UUID.randomUUID().toString())
                        .certificate(certificate)
                        .tokenId(record.getTokenId())
                        .amount(record.getAmount())
                        .build();
                certificateRecordRepository.save(detail);
                updateUserBalance(certificate.getUserId(), record.getTokenId(), record.getAmount());
            }
        } catch (Exception e) {
            log.error("Failed to fetch details from chain", e);
        }
    }

    private void updateUserBalance(String userAddress, BigInteger creditTokenId, BigInteger amount) {
        Object lock = walletLocks.computeIfAbsent(userAddress.toLowerCase(), k -> new Object());

        synchronized (lock) {
            // 1. Cập nhật WalletCredit (như cũ)
            WalletCredit walletCredit = walletCreditRepository
                    .findByWalletAddressAndTokenId(userAddress, creditTokenId.longValue())
                    .orElseThrow(() -> new RuntimeException("Wallet Credit not found for Token: " + creditTokenId));

            BigInteger currentAvailable = walletCredit.getAvailableBalance();

            if (currentAvailable.compareTo(amount) < 0) {
                log.warn("Data Inconsistency: DB Balance ({}) < Retired Amount ({}) for User {}",
                        currentAvailable, amount, userAddress);
                walletCredit.setAvailableBalance(currentAvailable.subtract(amount).max(BigInteger.ZERO));
            } else {
                walletCredit.setAvailableBalance(currentAvailable.subtract(amount));
            }

            walletCredit.setUpdatedAt(LocalDateTime.now());
            walletCreditRepository.save(walletCredit);
            log.info("Deducted {} from User {} for Token {}", amount, userAddress, creditTokenId);

            CarbonCredit carbonCredit = carbonCreditRepository
                    .findByTokenId(creditTokenId.longValue())
                    .orElseThrow(() -> new RuntimeException("Carbon Credit not found for Token: " + creditTokenId));

            long currentRetired = carbonCredit.getRetiredAmount();
            carbonCredit.setRetiredAmount(currentRetired + amount.longValue());
            carbonCreditRepository.save(carbonCredit);

            log.info("Updated retiredAmount to {} for Token {}", carbonCredit.getRetiredAmount(), creditTokenId);
        }
    }
}
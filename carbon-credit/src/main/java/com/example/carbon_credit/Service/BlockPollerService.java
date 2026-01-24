package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.BlockchainEventDTO;
import com.example.carbon_credit.Entity.IndexerState;
import com.example.carbon_credit.Kafka.KafkaProducerService;
import com.example.carbon_credit.Repository.IndexerStateRepository;
import com.example.carbon_credit.constants.ChainConstants;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockPollerService {

    private final Web3j web3j;
    private final KafkaProducerService kafkaProducer;
    private final IndexerStateRepository stateRepository;
    private final ChainConstants chainConstants;

    @Value("${indexer.confirmation-depth:1}")
    private int CONFIRMATION_DEPTH;

    private final int MAX_BATCH_SIZE = 100;

    @Value("${indexer.start-block:0}")
    private BigInteger START_BLOCK;

    @PostConstruct
    public void init() {
        try {
            log.info(" BlockPollerService initialized");
            log.info(" Confirmation depth: {} blocks", CONFIRMATION_DEPTH);
            log.info(" Start block: {}", START_BLOCK);
            log.info(" Listened contracts: {}", chainConstants.getListenedAddresses());

            if (!stateRepository.existsById(ChainConstants.INDEXER_ID)) {
                stateRepository.save(new IndexerState(ChainConstants.INDEXER_ID, START_BLOCK));
                log.info(" Initialized Indexer State at block: {}", START_BLOCK);
            } else {
                IndexerState state = stateRepository.findById(ChainConstants.INDEXER_ID).orElseThrow();
                log.info(" Resuming from block: {}", state.getLastScannedBlock());
            }
        } catch (Exception e) {
            log.error(" Failed to initialize BlockPollerService: {}", e.getMessage(), e);
            throw new RuntimeException("BlockPollerService initialization failed", e);
        }
    }

    @Scheduled(fixedDelay = 3000)
    public void pollBlocks() {
        try {
            if (web3j == null) {
                log.error(" Web3j is null! Check RPC configuration.");
                return;
            }

            BigInteger currentNetworkBlock = web3j.ethBlockNumber().send().getBlockNumber();

            if (currentNetworkBlock == null) {
                log.error(" Failed to get current block number from RPC");
                return;
            }

            BigInteger safeBlock = currentNetworkBlock.subtract(BigInteger.valueOf(CONFIRMATION_DEPTH));

            if (safeBlock.compareTo(BigInteger.ZERO) < 0) {
                safeBlock = BigInteger.ZERO;
            }

            IndexerState state = stateRepository.findById(ChainConstants.INDEXER_ID).orElseThrow();
            BigInteger lastScanned = state.getLastScannedBlock();
            BigInteger fromBlock = lastScanned.add(BigInteger.ONE);

            if (fromBlock.compareTo(safeBlock) > 0) {
                log.debug(" Already synced. Last: {}, Current: {}, Safe: {}",
                        lastScanned, currentNetworkBlock, safeBlock);
                return;
            }

            BigInteger toBlock = fromBlock.add(BigInteger.valueOf(MAX_BATCH_SIZE));
            if (toBlock.compareTo(safeBlock) > 0) {
                toBlock = safeBlock;
            }

            log.info(" Scanning blocks: {} -> {} (Network: {}, Safe: {})",
                    fromBlock, toBlock, currentNetworkBlock, safeBlock);

            scanAndProcess(fromBlock, toBlock);

            state.setLastScannedBlock(toBlock);
            stateRepository.save(state);

            log.info(" Scanned successfully. Updated lastScannedBlock to: {}", toBlock);

        } catch (Exception e) {
            log.error(" Polling failed: {}", e.getMessage(), e);
        }
    }

    private void scanAndProcess(BigInteger from, BigInteger to) throws IOException {
        try {
            if (from == null || to == null) {
                log.error(" Invalid block range: from={}, to={}", from, to);
                return;
            }

            // Use getter to get listened addresses
            List<String> listenedAddresses = chainConstants.getListenedAddresses();

            if (listenedAddresses == null || listenedAddresses.isEmpty()) {
                log.error(" No contracts to listen! Check configuration");
                return;
            }

            log.debug(" Filtering events from contracts: {}", listenedAddresses);

            EthFilter filter = new EthFilter(
                    DefaultBlockParameter.valueOf(from),
                    DefaultBlockParameter.valueOf(to),
                    listenedAddresses);

            EthLog ethLog = web3j.ethGetLogs(filter).send();

            if (ethLog.hasError()) {
                log.error(" RPC error: {}", ethLog.getError().getMessage());
                return;
            }

            List<EthLog.LogResult> logs = ethLog.getLogs();

            if (logs == null) {
                log.warn(" RPC returned null logs for blocks {} -> {}", from, to);
                return;
            }

            if (logs.isEmpty()) {
                log.debug(" No events found in blocks {} -> {}", from, to);
                return;
            }

            log.info(" Found {} events in blocks {} -> {}", logs.size(), from, to);

            for (EthLog.LogResult logResult : logs) {
                try {
                    if (logResult == null) {
                        log.warn(" Skipping null log result");
                        continue;
                    }

                    Log logData = (Log) logResult.get();

                    if (logData == null) {
                        log.warn(" Skipping null log data");
                        continue;
                    }

                    processSingleLog(logData);

                } catch (Exception e) {
                    log.error(" Failed to process log: {}", e.getMessage(), e);
                }
            }

        } catch (IOException e) {
            log.error(" RPC call failed: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error(" Unexpected error in scanAndProcess: {}", e.getMessage(), e);
        }
    }

    private void processSingleLog(Log logData) {
        try {
            if (logData.getTopics() == null || logData.getTopics().isEmpty()) {
                log.warn(" Log has no topics, skipping");
                return;
            }

            String eventHash = logData.getTopics().get(0);
            String eventType = identifyEventType(eventHash);

            if (eventType == null) {
                log.debug(" Unknown event hash: {}", eventHash);
                return;
            }

            log.info(" Event detected: {} | Block: {} | TxHash: {}",
                    eventType,
                    logData.getBlockNumber(),
                    logData.getTransactionHash());

            BlockchainEventDTO eventDTO = new BlockchainEventDTO();
            eventDTO.setTransactionHash(logData.getTransactionHash());
            eventDTO.setBlockNumber(logData.getBlockNumber());
            eventDTO.setContractAddress(logData.getAddress());
            eventDTO.setEventType(eventType);
            eventDTO.setTopics(logData.getTopics());
            eventDTO.setData(logData.getData());

            kafkaProducer.sendOnChainEvent(eventDTO);
            log.info(" Sent {} to Kafka", eventType);

        } catch (Exception e) {
            log.error(" Failed to process single log: {}", e.getMessage(), e);
        }
    }

    private String identifyEventType(String hash) {
        if (hash == null)
            return null;

        if (hash.equals(ChainConstants.SuperAdmin_Transferred_Hash))
            return "SUPERADMIN_TRANSFERRED";
        if (hash.equals(ChainConstants.Admin_Added_Hash))
            return "ADMIN_ADDED";
        if (hash.equals(ChainConstants.Admin_Removed_Hash))
            return "ADMIN_REMOVED";
        if (hash.equals(ChainConstants.Government_Added_Hash))
            return "GOVERNMENT_ADDED";
        if (hash.equals(ChainConstants.Government_Removed_Hash))
            return "GOVERNMENT_REMOVED";
        if (hash.equals(ChainConstants.Organization_Verified_Hash))
            return "ORGANIZATION_VERIFIED";
        if (hash.equals(ChainConstants.Organization_Revoked_Hash))
            return "ORGANIZATION_REVOKED";
        if (hash.equals(ChainConstants.CreditQuota_Updated_Hash))
            return "CREDITQUOTA_UPDATED";
        if (hash.equals(ChainConstants.Project_Approved_Hash))
            return "PROJECT_APPROVED";
        if (hash.equals(ChainConstants.Credit_Minted_Hash))
            return "CREDIT_MINTED";
        if (hash.equals(ChainConstants.Credit_Retired_Hash))
            return "CREDIT_RETIRED";
        if (hash.equals(ChainConstants.Certificate_Minted_Hash))
            return "CERTIFICATE_MINTED";
        if (hash.equals(ChainConstants.Batch_Certificate_Retired_Hash))
            return "BATCH_CERTIFICATE_RETIRED";
        if (hash.equals(ChainConstants.Project_Revoked_Hash))
            return "PROJECT_REVOKED";
        if (hash.equals(ChainConstants.Native_Deposited_Hash))
            return "NATIVE_DEPOSITED";
        if (hash.equals(ChainConstants.Native_Withdrawn_Hash))
            return "NATIVE_WITHDRAWN";
        if (hash.equals(ChainConstants.Credit_Deposited_Hash))
            return "CREDIT_DEPOSITED";
        if (hash.equals(ChainConstants.Credit_Withdrawn_Hash))
            return "CREDIT_WITHDRAWN";
        if (hash.equals(ChainConstants.Balance_Locked_Hash))
            return "BALANCE_LOCKED";
        if (hash.equals(ChainConstants.Balance_Unlocked_Hash))
            return "BALANCE_UNLOCKED";
        if (hash.equals(ChainConstants.Trade_Settled_Hash))
            return "TRADE_SETTLED";
        if (hash.equals(ChainConstants.Batch_Settled_Hash))
            return "BATCH_SETTLED";
        if (hash.equals(ChainConstants.Settlement_Operator_Updated_Hash))
            return "SETTLEMENT_UPDATED";

        return null;
    }
}
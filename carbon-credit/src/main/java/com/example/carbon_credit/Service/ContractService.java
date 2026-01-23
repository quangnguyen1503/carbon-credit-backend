package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.CertificateRecordDTO;
import com.example.carbon_credit.DTO.TradeDTO;
import com.example.carbon_credit.Entity.Certificate;
import com.example.carbon_credit.Entity.CertificateRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractService {
    private final Web3j web3j;

    @Value("${blockchain.contract.exchange.address}")
    private String exchangeContractAddress;

    @Value("${blockchain.contract.system.address}")
    private String systemContractAddress;

    @Value("${blockchain.settlement.operator.private-key}")
    private String settlementOperatorPrivateKey;

    @Value("${web3.chain-id}")
    private Long chainId;

    private static final BigInteger GAS_PRICE = BigInteger.valueOf(50_000_000_000L);
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(500_000L);

    public BigInteger getNativeBalance(String userAddress) throws Exception {
        Function function = new Function(
                "getNativeBalance",
                Arrays.asList(new Address(userAddress)),
                Arrays.asList(new TypeReference<Uint256>() {
                }));

        String encodedFunction = FunctionEncoder.encode(function);

        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        userAddress,
                        exchangeContractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST).send();

        if (response.hasError()) {
            throw new RuntimeException("Error calling getNativeBalance: " + response.getError().getMessage());
        }

        String value = response.getValue();
        if (value == null || value.equals("0x") || value.length() <= 2) {
            return BigInteger.ZERO;
        }

        try {
            List<Type> results = FunctionReturnDecoder.decode(
                    value,
                    function.getOutputParameters());

            if (results.isEmpty()) {
                return BigInteger.ZERO;
            }

            BigInteger balance = (BigInteger) results.get(0).getValue();
            return balance;

        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    public BigInteger getCreditBalance(String userAddress, BigInteger creditTokenId) throws Exception {
        Function function = new Function(
                "getCreditBalance",
                Arrays.asList(new Uint256(creditTokenId), new Address(userAddress)),
                Arrays.asList(new TypeReference<Uint256>() {
                }));

        String encodedFunction = FunctionEncoder.encode(function);

        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(
                        userAddress,
                        exchangeContractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST).send();

        if (response.hasError()) {
            throw new RuntimeException("Error calling getCreditBalance: " + response.getError().getMessage());
        }

        String value = response.getValue();
        if (value == null || value.equals("0x") || value.length() <= 2) {
            return BigInteger.ZERO;
        }

        try {
            List<Type> results = FunctionReturnDecoder.decode(
                    value,
                    function.getOutputParameters());

            if (results.isEmpty()) {
                return BigInteger.ZERO;
            }

            BigInteger balance = (BigInteger) results.get(0).getValue();
            return balance;

        } catch (Exception e) {
            return BigInteger.ZERO;
        }
    }

    public TransactionReceipt lockBalance(
            String orderId,
            String userAddress,
            BigInteger tokenId,
            BigInteger amount,
            boolean isCreditToken) throws Exception {
        Credentials credentials = Credentials.create(settlementOperatorPrivateKey);


        TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(
                web3j,
                3000, // Poll every 3 seconds
                40 // Max 40 attempts = 2 minutes
        );

        TransactionManager txManager = new RawTransactionManager(web3j, credentials, chainId, receiptProcessor);

        Function function = new Function(
                "lockBalance",
                Arrays.asList(
                        new Utf8String(orderId),
                        new Address(userAddress),
                        new Uint256(tokenId),
                        new Uint256(amount),
                        new Bool(isCreditToken)),
                Arrays.asList());

        String encodedFunction = FunctionEncoder.encode(function);
        EthSendTransaction ethSendTransaction = txManager.sendTransaction(
                GAS_PRICE,
                GAS_LIMIT,
                exchangeContractAddress,
                encodedFunction,
                BigInteger.ZERO);

        if (ethSendTransaction.hasError()) {
            throw new RuntimeException(
                    "Error sending lockBalance transaction: " + ethSendTransaction.getError().getMessage());
        }

        String txHash = ethSendTransaction.getTransactionHash();
        TransactionReceipt receipt = waitForReceipt(txHash);

        if (receipt.isStatusOK()) {
            log.info(" Balance locked successfully");
            log.info(" Gas used: {} | Block: {}", receipt.getGasUsed(), receipt.getBlockNumber());
        } else {
            log.error(" Transaction reverted! Status: {}", receipt.getStatus(), receipt.getRevertReason());
            throw new RuntimeException("Transaction reverted - check smart contract conditions");
        }

        return receipt;
    }

    public TransactionReceipt unlockBalance(String orderId) throws Exception {
        Credentials credentials = Credentials.create(settlementOperatorPrivateKey);

        TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(
                web3j,
                3000, // Poll every 3 seconds
                40 // Max 40 attempts = 2 minutes
        );

        TransactionManager txManager = new RawTransactionManager(web3j, credentials, chainId, receiptProcessor);

        Function function = new Function(
                "unlockBalance",
                Arrays.asList(new Utf8String(orderId)),
                Arrays.asList());

        String encodedFunction = FunctionEncoder.encode(function);
        EthSendTransaction ethSendTransaction = txManager.sendTransaction(
                GAS_PRICE,
                GAS_LIMIT,
                exchangeContractAddress,
                encodedFunction,
                BigInteger.ZERO);

        if (ethSendTransaction.hasError()) {
            throw new RuntimeException(
                    "Error sending unlockBalance transaction: " + ethSendTransaction.getError().getMessage());
        }

        String txHash = ethSendTransaction.getTransactionHash();

        TransactionReceipt receipt = waitForReceipt(txHash);
        log.info("✅ Balance unlocked successfully");

        return receipt;
    }

    public TransactionReceipt processBatchSettlement(
            BigInteger batchId,
            List<TradeDTO> trades,
            List<String> buyOrderIds,
            List<String> sellOrderIds) throws Exception {
        Credentials credentials = Credentials.create(settlementOperatorPrivateKey);

        TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(
                web3j, 3000, 60);
        TransactionManager txManager = new RawTransactionManager(web3j, credentials, chainId, receiptProcessor);

        // Mapping TradeStruct
        List<StaticStruct> tradeStructs = trades.stream()
                .map(trade -> new StaticStruct(
                        new Address(trade.getBuyer()),
                        new Address(trade.getSeller()),
                        new Uint256(trade.getCreditTokenId()),
                        new Uint256(trade.getCreditAmount()),
                        new Uint256(trade.getTotalValue())))
                .collect(Collectors.toList());

        List<Utf8String> buyOrderIdTypes = buyOrderIds.stream()
                .map(Utf8String::new)
                .collect(Collectors.toList());

        List<Utf8String> sellOrderIdTypes = sellOrderIds.stream()
                .map(Utf8String::new)
                .collect(Collectors.toList());

        Function function = new Function(
                "processBatchSettlement",
                Arrays.asList(
                        new Uint256(batchId),
                        new DynamicArray<StaticStruct>(StaticStruct.class, tradeStructs),
                        new DynamicArray<>(Utf8String.class, buyOrderIdTypes),
                        new DynamicArray<>(Utf8String.class, sellOrderIdTypes)),
                Arrays.asList());

        String encodedFunction = FunctionEncoder.encode(function);

        // Tính toán Gas Limit động cho Batch (vì String tốn nhiều gas hơn)
        BigInteger baseBatchGas = BigInteger.valueOf(500_000);
        BigInteger gasPerTrade = BigInteger.valueOf(150_000); // Tăng estimate
        BigInteger batchGasLimit = baseBatchGas.add(gasPerTrade.multiply(BigInteger.valueOf(trades.size())));

        EthSendTransaction ethSendTransaction = txManager.sendTransaction(
                GAS_PRICE,
                batchGasLimit,
                exchangeContractAddress,
                encodedFunction,
                BigInteger.ZERO);

        if (ethSendTransaction.hasError()) {
            throw new RuntimeException(
                    "Error sending processBatchSettlement transaction: " + ethSendTransaction.getError().getMessage());
        }

        return waitForReceipt(ethSendTransaction.getTransactionHash());
    }

    private TransactionReceipt waitForReceipt(String txHash) throws Exception {
        int attempts = 0;
        int maxAttempts = 60; // 3 minutes
        int sleepTime = 3000; // 3 seconds

        log.info("⏳ Waiting for confirmation...");

        while (attempts < maxAttempts) {
            try {
                Optional<TransactionReceipt> receiptOptional = web3j
                        .ethGetTransactionReceipt(txHash)
                        .send()
                        .getTransactionReceipt();

                if (receiptOptional.isPresent()) {
                    log.info("✅ Confirmed after {} seconds", (attempts * sleepTime) / 1000);
                    return receiptOptional.get();
                }

                // Log progress every 5 attempts (15 seconds)
                if (attempts > 0 && attempts % 5 == 0) {
                    log.info("⏳ Still waiting... {}/{} attempts", attempts, maxAttempts);
                }

            } catch (Exception e) {
                log.warn("⚠️ Error checking receipt (attempt {}): {}", attempts + 1, e.getMessage());
            }

            Thread.sleep(sleepTime);
            attempts++;
        }

        // Timeout
        String error = String.format(
                "Transaction not mined after %d seconds. " +
                        "Check: https://amoy.polygonscan.com/tx/%s",
                (maxAttempts * sleepTime) / 1000,
                txHash);
        log.error("❌ {}", error);
        throw new RuntimeException(error);
    }

    // Hàm gọi view lockedBalances trên Smart Contract
    public String getLockedBalanceOwner(String orderId) {
        try {
            Function function = new Function(
                    "lockedBalances",
                    Arrays.asList(new Utf8String(orderId)),
                    Arrays.asList(
                            new TypeReference<Address>() {
                            },
                            new TypeReference<Bool>() {
                            },
                            new TypeReference<Uint256>() {
                            },
                            new TypeReference<Uint256>() {
                            },
                            new TypeReference<Bool>() {
                            }
                    ));

            String encodedFunction = FunctionEncoder.encode(function);
            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(null, exchangeContractAddress, encodedFunction),
                    DefaultBlockParameterName.LATEST).send();

            List<Type> result = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

            if (result.isEmpty())
                return "0x0000000000000000000000000000000000000000";

            // Trả về địa chỉ User đang sở hữu Lock này
            return result.get(0).getValue().toString().toLowerCase();

        } catch (Exception e) {
            log.error("Failed to fetch locked balance for {}: {}", orderId, e.getMessage());
            return "ERROR";
        }
    }

    public static class RetirementRecordStruct extends StaticStruct {
        public Uint256 certificateId;
        public Uint256 tokenId;
        public Uint256 creditAmount;

        public RetirementRecordStruct(Uint256 a, Uint256 b, Uint256 c) {
            super(a, b, c);
            this.certificateId = a;
            this.tokenId = b;
            this.creditAmount = c;
        }

        // Constructor rỗng bắt buộc
        public RetirementRecordStruct() {
            super(new Uint256(0), new Uint256(0), new Uint256(0));
        }
    }

    public List<CertificateRecordDTO> getCertificateRecords(BigInteger certificateId) {
        try {
            // 1. Định nghĩa Function call
            // Solidity: function getCertificateRecords(uint256) returns (RetirementRecord[])
            Function function = new Function(
                    "getCertificateRecords",
                    Arrays.asList(new Uint256(certificateId)),
                    Collections.singletonList(new TypeReference<DynamicArray<RetirementRecordStruct>>() {})
            );

            // 2. Encode function call
            String encodedFunction = FunctionEncoder.encode(function);

            String targetContract = systemContractAddress;

            EthCall response = web3j.ethCall(
                            Transaction.createEthCallTransaction(null, targetContract, encodedFunction),
                            DefaultBlockParameterName.LATEST)
                    .send();

            if (response.hasError()) {
                log.error("EthCall error getting certificate records: {}", response.getError().getMessage());
                return new ArrayList<>();
            }

            String value = response.getValue();
            log.info("🔍 Raw response value for CertID {}: {}", certificateId, value);

            if (value == null || value.equals("0x")) {
                return new ArrayList<>();
            }

            // 3. Decode dữ liệu trả về
            List<Type> result = FunctionReturnDecoder.decode(value, function.getOutputParameters());

            if (result.isEmpty()) {
                return new ArrayList<>();
            }

            // 4. Map từ Web3j Struct sang Java DTO
            List<CertificateRecordDTO> records = new ArrayList<>();


            // Kết quả trả về là một List chứa 1 phần tử, phần tử đó là DynamicArray
            List<RetirementRecordStruct> structList = (List<RetirementRecordStruct>) result.get(0).getValue();

            for (RetirementRecordStruct struct : structList) {
                records.add(new CertificateRecordDTO(
                        struct.certificateId.getValue(),
                        struct.tokenId.getValue(),
                        struct.creditAmount.getValue()
                ));
            }

            return records;

        } catch (Exception e) {
            log.error("Failed to fetch certificate records for ID {}: {}", certificateId, e.getMessage());
            return new ArrayList<>();
        }
    }
}
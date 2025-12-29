package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.TradeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractService {
    private final Web3j web3j;

    @Value("${blockchain.contract.exchange.address}")
    private String exchangeContractAddress;

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
            Arrays.asList(new TypeReference<Uint256>() {})
        );

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
                    function.getOutputParameters()
            );

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
            Arrays.asList(new TypeReference<Uint256>() {})
        );

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
                    function.getOutputParameters()
            );

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
        boolean isCreditToken
    ) throws Exception {
        Credentials credentials = Credentials.create(settlementOperatorPrivateKey);

        TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(
            web3j,
            3000,  // Poll every 3 seconds
            40     // Max 40 attempts = 2 minutes
        );

        TransactionManager txManager = new RawTransactionManager(web3j, credentials, chainId, receiptProcessor);

        byte[] orderIdBytes = convertToBytes32(orderId);

        Function function = new Function(
            "lockBalance",
            Arrays.asList(
                new Bytes32(orderIdBytes),
                new Address(userAddress),
                new Uint256(tokenId),
                new Uint256(amount),
                new Bool(isCreditToken)
            ),
            Arrays.asList()
        );

        String encodedFunction = FunctionEncoder.encode(function);
        EthSendTransaction ethSendTransaction = txManager.sendTransaction(
            GAS_PRICE,
            GAS_LIMIT,
            exchangeContractAddress,
            encodedFunction,
            BigInteger.ZERO
        );

        if (ethSendTransaction.hasError()) {
            throw new RuntimeException("Error sending lockBalance transaction: " + ethSendTransaction.getError().getMessage());
        }

        String txHash = ethSendTransaction.getTransactionHash();
        // TransactionReceipt receipt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt().orElseThrow(() ->
        //     new RuntimeException("Transaction receipt not generated after locking balance")
        // );
        TransactionReceipt receipt = waitForReceipt(txHash);

        if (receipt.isStatusOK()) {
            log.info("✅ Balance locked successfully");
            log.info("📊 Gas used: {} | Block: {}", receipt.getGasUsed(), receipt.getBlockNumber());
        } else {
            log.error("❌ Transaction reverted! Status: {}", receipt.getStatus());
            throw new RuntimeException("Transaction reverted - check smart contract conditions");
        }

        return receipt;
    }

    public TransactionReceipt unlockBalance(String orderId) throws Exception {
        Credentials credentials = Credentials.create(settlementOperatorPrivateKey);

        TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(
            web3j,
            3000,  // Poll every 3 seconds
            40     // Max 40 attempts = 2 minutes
        );

        TransactionManager txManager = new RawTransactionManager(web3j, credentials, chainId, receiptProcessor);

        // Convert orderId to bytes32
        byte[] orderIdBytes = convertToBytes32(orderId);

        Function function = new Function(
            "unlockBalance",
            Arrays.asList(new Bytes32(orderIdBytes)),
            Arrays.asList()
        );

        String encodedFunction = FunctionEncoder.encode(function);
        EthSendTransaction ethSendTransaction = txManager.sendTransaction(
            GAS_PRICE,
            GAS_LIMIT,
            exchangeContractAddress,
            encodedFunction,
            BigInteger.ZERO
        );

        if (ethSendTransaction.hasError()) {
            throw new RuntimeException("Error sending unlockBalance transaction: " + ethSendTransaction.getError().getMessage());
        }

        String txHash = ethSendTransaction.getTransactionHash();
        // TransactionReceipt receipt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt().orElseThrow(() ->
        //     new RuntimeException("Transaction receipt not generated after unlocking balance")
        // );
        TransactionReceipt receipt = waitForReceipt(txHash);
        log.info("✅ Balance unlocked successfully");

        return receipt;
    }

    public TransactionReceipt processBatchSettlement(
        BigInteger batchId,
        List<TradeDTO> trades,
        List<String> buyOrderIds,
        List<String> sellOrderIds
    ) throws Exception {
        Credentials credentials = Credentials.create(settlementOperatorPrivateKey);

        TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(
            web3j, 3000, 60  // 3 minutes for batch
        );
        TransactionManager txManager = new RawTransactionManager(web3j, credentials, chainId, receiptProcessor);

        List<DynamicStruct> tradeStructs = trades.stream()
            .map(trade -> new DynamicStruct(
                new Address(trade.getBuyer()),
                new Address(trade.getSeller()),
                new Uint256(trade.getCreditTokenId()),
                new Uint256(trade.getAmount()),
                new Uint256(trade.getTotalValue())

            )).toList();
        
        List<Bytes32> buyOrderIdTypes = buyOrderIds.stream()
            .map(id -> new Bytes32(convertToBytes32(id)))
            .toList();

        List<Bytes32> sellOrderIdTypes = sellOrderIds.stream()
            .map(id -> new Bytes32(convertToBytes32(id)))
            .toList();
        
        
        Function function = new Function(
            "processBatchSettlement",
            Arrays.asList(
                new Uint256(batchId),
                new DynamicArray<>(DynamicStruct.class, tradeStructs),
                new DynamicArray<>(org.web3j.abi.datatypes.generated.Bytes32.class, buyOrderIdTypes),
                new DynamicArray<>(org.web3j.abi.datatypes.generated.Bytes32.class, sellOrderIdTypes)
            ),
            Arrays.asList()
        );

        String encodedFunction = FunctionEncoder.encode(function);
        BigInteger batchGasLimit = GAS_LIMIT.multiply(BigInteger.valueOf(Math.max(trades.size(), 1)));

        EthSendTransaction ethSendTransaction = txManager.sendTransaction(
            GAS_PRICE,
            batchGasLimit,
            exchangeContractAddress,
            encodedFunction,
            BigInteger.ZERO
        );

        if (ethSendTransaction.hasError()) {
            throw new RuntimeException("Error sending processBatchSettlement transaction: " + ethSendTransaction.getError().getMessage());
        }

        String txHash = ethSendTransaction.getTransactionHash();
        // TransactionReceipt receipt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt()
        //     .orElseThrow(() -> new RuntimeException("Transaction receipt not generated after processing batch settlement")
        // );
        TransactionReceipt receipt = waitForReceipt(txHash);
        log.info("✅ Batch {} settled successfully", batchId);

        return receipt;
    }
    
    private byte[] convertToBytes32(String input) {
        byte[] result = new byte[32];
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        
        // Copy input bytes, truncate if > 32 bytes, pad with zeros if < 32 bytes
        int length = Math.min(inputBytes.length, 32);
        System.arraycopy(inputBytes, 0, result, 0, length);
        
        return result;
    }

    private TransactionReceipt waitForReceipt(String txHash) throws Exception {
        int attempts = 0;
        int maxAttempts = 60;  // 3 minutes
        int sleepTime = 3000;  // 3 seconds

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
            txHash
        );
        log.error("❌ {}", error);
        throw new RuntimeException(error);
    }
}
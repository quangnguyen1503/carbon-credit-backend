package com.example.carbon_credit.contract;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Java Wrapper được tối ưu hóa từ ABI của CarbonCreditExchange
 */
public class CarbonCreditExchange extends Contract {

    protected CarbonCreditExchange(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super("", contractAddress, web3j, credentials, contractGasProvider);
    }

    protected CarbonCreditExchange(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super("", contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static CarbonCreditExchange load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new CarbonCreditExchange(contractAddress, web3j, credentials, contractGasProvider);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 1. STRUCTS (Tương ứng với Tuple trong ABI)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static class Trade extends DynamicStruct {
        public String buyer;
        public String seller;
        public BigInteger creditTokenId;
        public BigInteger creditAmount;
        public BigInteger totalValue;

        public Trade(String buyer, String seller, BigInteger creditTokenId, BigInteger creditAmount, BigInteger totalValue) {
            super(new Address(buyer), new Address(seller), new Uint256(creditTokenId), new Uint256(creditAmount), new Uint256(totalValue));
            this.buyer = buyer;
            this.seller = seller;
            this.creditTokenId = creditTokenId;
            this.creditAmount = creditAmount;
            this.totalValue = totalValue;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 2. WRITE FUNCTIONS (Gửi giao dịch)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Nạp Native (ETH/MATIC) - Có msg.value
     */
    public RemoteFunctionCall<TransactionReceipt> depositNative(BigInteger weivalue) {
        final Function function = new Function("depositNative",
                Collections.emptyList(), Collections.emptyList());
        return executeRemoteCallTransaction(function, weivalue);
    }

    /**
     * Nạp Tín chỉ Carbon (ERC1155)
     */
    public RemoteFunctionCall<TransactionReceipt> depositCredit(BigInteger tokenId, BigInteger amount) {
        final Function function = new Function("depositCredit",
                Arrays.asList(new Uint256(tokenId), new Uint256(amount)), Collections.emptyList());
        return executeRemoteCallTransaction(function);
    }

    /**
     * Khóa số dư (Dành cho Settlement Operator)
     */
    public RemoteFunctionCall<TransactionReceipt> lockBalance(byte[] orderId, String user, BigInteger tokenId, BigInteger amount, Boolean isCreditToken) {
        final Function function = new Function("lockBalance",
                Arrays.asList(new Bytes32(orderId), new Address(user), new Uint256(tokenId), new Uint256(amount), new Bool(isCreditToken)),
                Collections.emptyList());
        return executeRemoteCallTransaction(function);
    }

    /**
     * Mở khóa số dư
     */
    public RemoteFunctionCall<TransactionReceipt> unlockBalance(byte[] orderId) {
        final Function function = new Function("unlockBalance",
                Arrays.asList(new Bytes32(orderId)), Collections.emptyList());
        return executeRemoteCallTransaction(function);
    }

    /**
     * Rút Native
     */
    public RemoteFunctionCall<TransactionReceipt> withdrawNative(BigInteger amount) {
        final Function function = new Function("withdrawNative",
                Arrays.asList(new Uint256(amount)), Collections.emptyList());
        return executeRemoteCallTransaction(function);
    }

    /**
     * Xử lý khớp lệnh theo đợt (Batch Settlement)
     * Quan trọng: Chuyển đổi List Java sang DynamicArray Web3j
     */
    public RemoteFunctionCall<TransactionReceipt> processBatchSettlement(
            BigInteger batchId,
            List<Trade> trades,
            List<byte[]> buyOrderIds,
            List<byte[]> sellOrderIds) {

        final Function function = new Function(
                "processBatchSettlement",
                Arrays.asList(
                        new Uint256(batchId),
                        new DynamicArray<>(Trade.class, trades),
                        new DynamicArray<>(Bytes32.class, buyOrderIds.stream().map(Bytes32::new).collect(Collectors.toList())),
                        new DynamicArray<>(Bytes32.class, sellOrderIds.stream().map(Bytes32::new).collect(Collectors.toList()))
                ),
                Collections.emptyList());
        return executeRemoteCallTransaction(function);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 3. READ FUNCTIONS (Truy vấn)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public RemoteFunctionCall<BigInteger> getNativeBalance(String user) {
        final Function function = new Function("getNativeBalance",
                Arrays.asList(new Address(user)),
                Arrays.asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> getCreditBalance(BigInteger tokenId, String user) {
        final Function function = new Function("getCreditBalance",
                Arrays.asList(new Uint256(tokenId), new Address(user)),
                Arrays.asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<Boolean> paused() {
        final Function function = new Function("paused",
                Collections.emptyList(),
                Arrays.asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 4. EVENT DEFINITIONS (Dành cho Listener)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static final Event NATIVEDEPOSITED_EVENT = new Event("NativeDeposited",
            Arrays.asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>(false) {}));

    public static final Event CREDITDEPOSITED_EVENT = new Event("CreditDeposited",
            Arrays.asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>(true) {}, new TypeReference<Uint256>(false) {}));

    public static final Event NATIVEWITHDRAWN_EVENT = new Event("NativeWithdrawn",
            Arrays.asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>(false) {}));

    public static final Event TRADESETTLED_EVENT = new Event("TradeSettled",
            Arrays.asList(
                    new TypeReference<Uint256>(true) {}, // batchId
                    new TypeReference<Address>(true) {}, // buyer
                    new TypeReference<Address>(true) {}, // seller
                    new TypeReference<Uint256>(false) {}, // creditTokenId
                    new TypeReference<Uint256>(false) {}, // amount
                    new TypeReference<Uint256>(false) {}  // totalValue
            ));
}
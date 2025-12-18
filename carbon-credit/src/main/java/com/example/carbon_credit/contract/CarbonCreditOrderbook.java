package com.example.carbon_credit.contract;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
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

/**
 * Minimal Web3j wrapper for the CarbonCreditOrderbook contract.
 * Only the functions used by the backend are exposed here.
 */
public class CarbonCreditOrderbook extends Contract {

    private static final String BINARY = Contract.BIN_NOT_PROVIDED;

    public static final String FUNC_SETTLETRADE = "settleTrade";
    public static final String FUNC_CARBONESCROW = "carbonEscrow";

    protected CarbonCreditOrderbook(String contractAddress,
                                    Web3j web3j,
                                    Credentials credentials,
                                    ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    protected CarbonCreditOrderbook(String contractAddress,
                                    Web3j web3j,
                                    TransactionManager transactionManager,
                                    ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static CarbonCreditOrderbook load(
            String contractAddress,
            Web3j web3j,
            Credentials credentials,
            ContractGasProvider contractGasProvider
    ) {
        return new CarbonCreditOrderbook(contractAddress, web3j, credentials, contractGasProvider);
    }

    public RemoteFunctionCall<TransactionReceipt> settleTrade(
            String buyer,
            String seller,
            BigInteger amount,
            BigInteger price
    ) {
        // Validate addresses trước khi parse
        if (buyer == null || seller == null) {
            throw new IllegalArgumentException("Buyer and seller addresses cannot be null");
        }
        
        // Normalize addresses (lowercase, ensure 0x prefix)
        buyer = normalizeAddress(buyer);
        seller = normalizeAddress(seller);
        
        // Validate format
        if (!buyer.matches("0x[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Invalid buyer address format: " + buyer + " (expected 0x followed by 40 hex characters)");
        }
        if (!seller.matches("0x[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Invalid seller address format: " + seller + " (expected 0x followed by 40 hex characters)");
        }
        
        final Function function = new Function(
                FUNC_SETTLETRADE,
                Arrays.asList(
                        new Address(160, buyer),
                        new Address(160, seller),
                        new Uint256(amount),
                        new Uint256(price)
                ),
                Collections.emptyList()
        );

        return executeRemoteCallTransaction(function);
    }
    
    /**
     * Normalize Ethereum address: lowercase và đảm bảo có 0x prefix
     */
    private String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        address = address.trim();
        // Nếu không có 0x prefix, thêm vào
        if (!address.startsWith("0x") && !address.startsWith("0X")) {
            address = "0x" + address;
        }
        // Convert to lowercase
        return address.toLowerCase();
    }

    public RemoteFunctionCall<BigInteger> carbonEscrow(String user) {
        final Function function = new Function(
                FUNC_CARBONESCROW,
                Collections.singletonList(new Address(160, user)),
                Collections.singletonList(new TypeReference<Uint256>() {})
        );

        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }
}


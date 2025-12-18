package com.example.carbon_credit.Service;

import com.example.carbon_credit.contract.CarbonCreditOrderbook;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigInteger;
@Service
@RequiredArgsConstructor
public class ContractService {

    private final Web3j web3j;
    private final Credentials credentials;

    @Value("${web3.contractAddress}")
    private String contractAddress;

    private CarbonCreditOrderbook contract;

    @PostConstruct
    public void init() {
        if (contractAddress == null || contractAddress.isEmpty() || contractAddress.contains("<")) {
            throw new IllegalStateException("web3.contractAddress chưa được cấu hình đúng trong application.properties");
        }
        try {
            contract = CarbonCreditOrderbook.load(
                    contractAddress,
                    web3j,
                    credentials,
                    new StaticGasProvider(BigInteger.valueOf(1_000_000_000L), BigInteger.valueOf(3_000_000L))
            );
        } catch (Exception e) {
            throw new IllegalStateException("Không thể kết nối với blockchain. Kiểm tra web3.rpc và web3.contractAddress", e);
        }
    }
    // Thêm vào ContractService.java (sau method init())
    public String testRpcConnection() {
        try {
            String version = web3j.web3ClientVersion().send().getWeb3ClientVersion();
            return "Connected to blockchain! Client version: " + version;  // Ví dụ: "Geth/v1.13.5-stable..."
        } catch (Exception e) {
            return "Connection failed: " + e.getMessage();  // Ví dụ: "Timeout" hoặc "Invalid API key"
        }
    }
    // Thêm vào ContractService.java
    public String testLoadContract() {
        try {
            if (contract == null) {
                return "Contract not loaded – check @PostConstruct log";
            }
            String address = contract.getContractAddress();
            return "Contract loaded at address: " + address;
        } catch (Exception e) {
            return "Load failed: " + e.getMessage();  // Ví dụ: "Invalid contract address"
        }
    }

    public String settleTrade(String buyer, String seller, BigInteger amount, BigInteger price) throws Exception {
        TransactionReceipt tx = contract.settleTrade(
                buyer, seller,
                amount,  // BigInteger trực tiếp
                price    // BigInteger trực tiếp
        ).send();
        return tx.getTransactionHash();
    }

    public BigInteger getCarbonEscrow(String user) throws Exception {
        return contract.carbonEscrow(user).send();  // Đổi từ escrowERC20 thành carbonEscrow
    }
}






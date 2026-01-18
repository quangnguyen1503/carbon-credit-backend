package com.example.carbon_credit.Config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
@Slf4j
public class Web3Config {

    @Value("${web3.rpc-url}")
    private String rpcUrl;

    @Value("${web3.chain-id}")
    private Long chainId;

    @Bean
    public Web3j web3j() {
        log.info("🔗 Connecting to blockchain...");
        log.info("📍 RPC URL: {}", rpcUrl);
        log.info("🌐 Chain ID: {}", chainId);

        try {
            Web3j web3j = Web3j.build(new HttpService(rpcUrl));

            // Test connection
            String clientVersion = web3j.web3ClientVersion().send().getWeb3ClientVersion();
            log.info("✅ Connected to: {}", clientVersion);

            return web3j;
        } catch (Exception e) {
            log.error("❌ Failed to connect to blockchain: {}", e.getMessage());
            throw new RuntimeException("Cannot connect to blockchain", e);
        }
    }
}
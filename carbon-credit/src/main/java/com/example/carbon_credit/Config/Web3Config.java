package com.example.carbon_credit.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class Web3Config {

    @Value("${web3.rpc}")
    private String rpcUrl;

    @Value("${web3.privateKey}")
    private String privateKey;

    @Bean
    public Web3j web3j() {
        // Kiểm tra và loại bỏ dấu <> nếu có
        if (rpcUrl != null && rpcUrl.startsWith("<") && rpcUrl.endsWith(">")) {
            rpcUrl = rpcUrl.substring(1, rpcUrl.length() - 1);
        }
        
        if (rpcUrl == null || rpcUrl.isEmpty() || 
            rpcUrl.contains("<YOUR_INFURA_KEY>") || 
            rpcUrl.equals("https://sepolia.infura.io/v3/")) {
            throw new IllegalStateException(
                "web3.rpc chưa được cấu hình đúng trong application.properties. " +
                "Vui lòng thay <YOUR_INFURA_KEY> bằng Infura API key thực tế của bạn."
            );
        }
        return Web3j.build(new HttpService(rpcUrl));
    }

    @Bean
    public Credentials credentials() {
        // Kiểm tra và loại bỏ dấu <> nếu có
        if (privateKey != null && privateKey.startsWith("<") && privateKey.endsWith(">")) {
            privateKey = privateKey.substring(1, privateKey.length() - 1);
        }
        
        if (privateKey == null || privateKey.isEmpty() || 
            privateKey.contains("<MATCHER_ADMIN_PRIVATE_KEY>") ||
            privateKey.length() < 64) {
            throw new IllegalStateException(
                "web3.privateKey chưa được cấu hình đúng trong application.properties. " +
                "Vui lòng thay <MATCHER_ADMIN_PRIVATE_KEY> bằng private key thực tế (64 ký tự hex, không có 0x)."
            );
        }
        return Credentials.create(privateKey);
    }
}

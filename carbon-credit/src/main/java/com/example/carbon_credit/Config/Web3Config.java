package com.example.carbon_credit.Config;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class Web3Config {

    // Sử dụng đúng các key đã thống nhất ở application.properties
    @Value("${web3.rpc-url}")
    private String rpcUrl;

    @Value("${blockchain.settlement.operator.private-key}")
    private String privateKey;

    @Bean
    public Web3j web3j() {
        log.info("🌐 Đang khởi tạo kết nối Web3j tới: {}", rpcUrl);

        // Làm sạch dữ liệu cấu hình (loại bỏ khoảng trắng hoặc ký tự bao quanh lỗi)
        String cleanedUrl = cleanConfig(rpcUrl);

        if (cleanedUrl == null || cleanedUrl.isEmpty() || cleanedUrl.contains("YOUR_")) {
            throw new IllegalStateException("❌ Lỗi: 'web3.rpc-url' chưa được cấu hình đúng trong application.properties");
        }

        // Tối ưu hóa HttpClient: Blockchain thường phản hồi chậm, cần tăng Timeout
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        return Web3j.build(new HttpService(cleanedUrl, okHttpClient));
    }

    @Bean
    public Credentials credentials() {
        log.info("🔑 Đang khởi tạo Credentials cho Settlement Operator...");

        String cleanedKey = cleanConfig(privateKey);

        if (cleanedKey == null || cleanedKey.isEmpty() || cleanedKey.length() < 64) {
            throw new IllegalStateException("❌ Lỗi: 'blockchain.settlement.operator.private-key' không hợp lệ (phải là 64 ký tự hex)");
        }

        try {
            return Credentials.create(cleanedKey);
        } catch (Exception e) {
            log.error("❌ Không thể tạo Credentials từ Private Key: {}", e.getMessage());
            throw new IllegalStateException("Private Key không đúng định dạng hex.");
        }
    }

    /**
     * Hàm hỗ trợ làm sạch cấu hình, loại bỏ dấu < > hoặc khoảng trắng dư thừa
     */
    private String cleanConfig(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.startsWith("<") && cleaned.endsWith(">")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }
}
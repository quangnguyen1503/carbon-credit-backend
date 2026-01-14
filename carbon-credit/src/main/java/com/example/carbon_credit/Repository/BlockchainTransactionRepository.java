package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.BlockchainTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlockchainTransactionRepository extends JpaRepository<BlockchainTransaction, String> {
    // txHash là khóa chính nên findById có sẵn sẽ dùng txHash.

    // Tìm các giao dịch theo trạng thái (ví dụ: tìm các lệnh PENDING để kiểm tra confirm)
    List<BlockchainTransaction> findByStatus(String status);

    // Tìm lịch sử nạp/rút của một ví
    List<BlockchainTransaction> findByWalletAddressOrderByCreatedAtDesc(String walletAddress);
}

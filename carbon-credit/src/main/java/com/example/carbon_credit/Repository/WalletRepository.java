package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, String> {
    Optional<Wallet> findByAddress(String address);

    // Kiểm tra ví đã tồn tại chưa
    boolean existsByAddress(String address);
}

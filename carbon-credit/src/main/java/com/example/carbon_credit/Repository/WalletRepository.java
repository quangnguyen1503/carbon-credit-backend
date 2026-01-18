package com.example.carbon_credit.Repository;


import com.example.carbon_credit.DTO.MyNativeResponse;
import com.example.carbon_credit.Entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, String> {


    Optional<Wallet> findByAddress(String address);

    @Query("""
                SELECT new com.example.carbon_credit.DTO.MyNativeResponse(
                    w.nativeBalance
                )
                FROM Wallet w
                WHERE w.address = :address
            """)
    Optional<MyNativeResponse> findBalanceByAddress(String address);
}
package com.example.carbon_credit.Repository;

import com.example.carbon_credit.DTO.MyCreditResponse;
import com.example.carbon_credit.Entity.WalletCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public interface WalletCreditRepository
        extends JpaRepository<WalletCredit, String> {

    // Tìm số dư Credit dựa trên ví và Token ID (ERC1155)
    // Sử dụng Query để join bảng carbon_credits
    @Query("SELECT wc FROM WalletCredit wc " +
            "WHERE wc.wallet.address = :address " +
            "AND wc.carbonCredit.tokenId = :tokenId")
    Optional<WalletCredit> findByWalletAddressAndTokenId(
            @Param("address") String address,
            @Param("tokenId") BigInteger tokenId
    );

    // Tìm tất cả các loại credit trong một ví
    // List<WalletCredit> findByWalletId(String walletId);
}


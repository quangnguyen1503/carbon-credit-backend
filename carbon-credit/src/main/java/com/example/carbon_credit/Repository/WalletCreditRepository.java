package com.example.carbon_credit.Repository;

import com.example.carbon_credit.DTO.MyCreditResponse;
import com.example.carbon_credit.Entity.WalletCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WalletCreditRepository
        extends JpaRepository<WalletCredit, String> {

    @Query("""
        SELECT new com.example.carbon_credit.DTO.MyCreditResponse
                                                           (
            c.id,
            c.tokenId,
            c.projectId,
            wc.balance
        )
        FROM WalletCredit wc
        JOIN Wallet w ON wc.walletId = w.id
        JOIN CarbonCredit c ON wc.creditId = c.id
        WHERE w.userId = :userId
          AND wc.balance > 0
    """)
    List<MyCreditResponse> findMyCredits(@Param("userId") String userId);
}


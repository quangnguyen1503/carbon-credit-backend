package com.example.carbon_credit.Repository;

import com.example.carbon_credit.DTO.MyCreditResponse;
import com.example.carbon_credit.Entity.CarbonCredit;
import com.example.carbon_credit.Entity.Wallet;
import com.example.carbon_credit.Entity.WalletCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletCreditRepository extends JpaRepository<WalletCredit, String> {

  @Query("""
          SELECT new com.example.carbon_credit.DTO.MyCreditResponse(
              c.id,
              c.tokenId,
              c.projectId,
              p.name,
              wc.availableBalance
          )
          FROM WalletCredit wc
          JOIN wc.wallet w
          JOIN wc.carbonCredit c
          JOIN Project p ON p.id = c.projectId
          WHERE w.address = :walletAddress
            AND wc.availableBalance > 0
          ORDER BY c.tokenId
      """)
  List<MyCreditResponse> findMyCredits(@Param("walletAddress") String walletAddress);

  @Query("""
          SELECT wc
          FROM WalletCredit wc
          JOIN FETCH wc.wallet w
          JOIN FETCH wc.carbonCredit c
          WHERE w.address = :walletAddress
      """)
  List<WalletCredit> findByWalletAddress(@Param("walletAddress") String walletAddress);

  Optional<WalletCredit> findByWalletAndCarbonCredit(Wallet wallet, CarbonCredit carbonCredit);

  @Query("""
          SELECT wc
          FROM WalletCredit wc
          JOIN wc.wallet w
          JOIN wc.carbonCredit c
          WHERE w.address = :walletAddress
            AND c.tokenId = :tokenId
      """)
  Optional<WalletCredit> findByWalletAddressAndTokenId(
      @Param("walletAddress") String walletAddress,
      @Param("tokenId") Long tokenId);
}

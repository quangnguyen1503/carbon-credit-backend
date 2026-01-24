package com.example.carbon_credit.Repository;

import com.example.carbon_credit.DTO.CertificateDetailResponse;
import com.example.carbon_credit.Entity.Certificate;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, String> {

    Optional<Certificate> findById(String id);

    List<Certificate> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<Certificate> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query("""
                SELECT DISTINCT new com.example.carbon_credit.DTO.CertificateDetailResponse(
                    cert.id,
                    cert.userId,
                    cert.totalAmount,
                    cert.txHash,
                    cert.nftTokenId,
                    cert.createdAt
                )
                FROM Certificate cert
                   
                    WHERE cert.id = :certId
            """)
    Optional<CertificateDetailResponse> findCertificateDetailById(@Param("certId") String certId);


    @Query("""
                SELECT 
                    r.tokenId,
                    r.amount,
                    p.name
                FROM Certificate cert
                    JOIN cert.records r
                    JOIN CarbonCredit cc ON cc.tokenId = r.tokenId
                    JOIN Project p ON p.id = cc.projectId
                WHERE cert.id = :certId
            """)
    List<Object[]> findCertificateRecords(@Param("certId") String certId);


}

package com.example.carbon_credit.Repository;

import com.example.carbon_credit.DTO.ProjectResponse;
import com.example.carbon_credit.Entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    // ✅ ĐÃ SỬA: Lấy danh sách project của 1 user (Đầy đủ 17 tham số để khớp với DTO)
    @Query("""
        SELECT new com.example.carbon_credit.DTO.ProjectResponse(
            p.id, p.name, p.vintage, p.location, p.type, p.expectedCredits, p.description, p.ipfsHash, p.nftTokenId,
            p.createdAt, p.status, p.onchainHash,
            COALESCE(c.issueAmount, 0L),
            COALESCE(c.retiredAmount, 0L),
            uOwner.name,
            uVerify.name,
            uGov.name
        )
        FROM Project p
        LEFT JOIN CarbonCredit c ON c.projectId = p.id
        JOIN User uOwner ON p.ownerId = uOwner.id
        LEFT JOIN User uVerify ON p.verifiedBy = uVerify.id
        LEFT JOIN User uGov ON p.approvedBy = uGov.id
        WHERE p.ownerId = :ownerId
        ORDER BY p.createdAt DESC
    """)
    List<ProjectResponse> findAllByOwnerId(@Param("ownerId") String ownerId);

    // ✅ Lấy chi tiết 1 project theo ID (Dùng DTO)
    @Query("""
        SELECT new com.example.carbon_credit.DTO.ProjectResponse(
            p.id, p.name, p.vintage, p.location, p.type, p.expectedCredits, p.description, p.ipfsHash, p.nftTokenId,
            p.createdAt, p.status, p.onchainHash,
            COALESCE(cc.issueAmount, 0L),
            COALESCE(cc.retiredAmount, 0L),
            uOwner.name,
            uVerify.name,
            uGov.name
        )
        FROM Project p
        LEFT JOIN CarbonCredit cc ON p.id = cc.projectId
        JOIN User uOwner ON p.ownerId = uOwner.id
        LEFT JOIN User uVerify ON p.verifiedBy = uVerify.id
        LEFT JOIN User uGov ON p.approvedBy = uGov.id
        WHERE p.id = :projectId
    """)
    Optional<ProjectResponse> findProjectResponseById(@Param("projectId") String projectId);

    // ✅ Lấy tất cả project (Dùng cho Marketplace hoặc Admin)
    @Query("""
        SELECT new com.example.carbon_credit.DTO.ProjectResponse(
            p.id, p.name, p.vintage, p.location, p.type, p.expectedCredits, p.description, p.ipfsHash, p.nftTokenId,
            p.createdAt, p.status, p.onchainHash,
            COALESCE(cc.issueAmount, 0L),
            COALESCE(cc.retiredAmount, 0L),
            uOwner.name,
            uVerify.name,
            uGov.name
        )
        FROM Project p
        LEFT JOIN CarbonCredit cc ON p.id = cc.projectId
        JOIN User uOwner ON p.ownerId = uOwner.id
        LEFT JOIN User uVerify ON p.verifiedBy = uVerify.id
        LEFT JOIN User uGov ON p.approvedBy = uGov.id
    """)
    List<ProjectResponse> findAllProjectResponses();

    // --- Các hàm Query Method cơ bản (Trả về Entity Project) ---

    List<Project> findByStatus(String status);

    List<Project> findByStatusAndVerifierRoleId(String status, String verifierRoleId);

    List<Project> findByStatusIn(List<String> statuses);

    List<Project> findByOwnerId(String userId);
}
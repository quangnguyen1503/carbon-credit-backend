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
    // ✅ Lấy danh sách project của 1 user + issue/retire amount
    @Query("""
        SELECT new com.example.carbon_credit.DTO.ProjectResponse(
            p.id,
            p.name,
            p.vintage,
            p.location,
            p.type,
            p.expectedCredits,
            p.description,
            p.ipfsHash,
            p.nftTokenId,
            p.createdAt,
            p.status,
            p.onchainHash,
            COALESCE(SUM(c.issueAmount), 0),
            COALESCE(SUM(c.retiredAmount), 0)
        )
        FROM Project p
        LEFT JOIN CarbonCredit c
            ON c.projectId = p.id
        WHERE p.ownerId = :ownerId
        GROUP BY
            p.id,
            p.name,
            p.vintage,
            p.location,
            p.type,
            p.expectedCredits,
            p.description,
            p.ipfsHash,
            p.nftTokenId,
            p.createdAt,
            p.status,
            p.onchainHash
        ORDER BY p.createdAt DESC
    """)
    List<ProjectResponse> findAllByOwnerId(@Param("ownerId") String ownerId);


    // ✅ Lấy project theo status (Entity)
    List<Project> findByStatus(String status);

    List<Project> findByStatusAndVerifierRoleId(String status ,String verifierRoleId);

    Optional<Project> findById(String projectId);


}


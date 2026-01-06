package com.example.carbon_credit.Repository;

import com.example.carbon_credit.DTO.ProjectResponse;
import com.example.carbon_credit.Entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {
    List<ProjectResponse> findByStatus(String status);

    List<ProjectResponse> findByOwnerId(String userId);
}


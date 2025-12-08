package com.example.carbon_credit.Service.impl;

import com.example.carbon_credit.DTO.VerifyRequestDTO;
import com.example.carbon_credit.Entity.Project;
import com.example.carbon_credit.Repository.ProjectRepository;
import com.example.carbon_credit.Service.ProjectService;
import com.example.carbon_credit.constants.ProjectStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProjectServiceImpl implements ProjectService {
    private final ProjectRepository projectRepository;


    @Autowired
    public ProjectServiceImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }
    @Override
    public Project getProject(String id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));
    }
    @Override
    public Project saveProject(Project project) {
//        project.setCreatedAt(Instant.from(java.time.LocalDateTime.now()));
        return projectRepository.save(project);
    }
    @Override
    public List<Project> getAllProject() {
        return projectRepository.findAll();
    }

    // Trong ProjectServiceImpl.java
    @Override
    public Project VerifyProject(String id, VerifyRequestDTO req, String verifyName) {  // Đổi tên param cho rõ: verifyName thay VerifyName
        Project project = getProject(id);
        if (!project.getStatus().equals(ProjectStatus.SUBMITTED)) {
            throw new RuntimeException("Project is not ready for verification");
        }

        // Set verifiedBy = verifier's address (hoặc name nếu cần)
        project.setVerifiedBy(verifyName);  // ← THÊM DÒNG NÀY!

        if (req.isApproved()) {
            project.setStatus(ProjectStatus.VERIFIED);
        } else {
            project.setStatus(ProjectStatus.REJECTED_BY_VERIFIER);
        }

        project.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(project);
    }
    @Override
    public Project ApprovedProject(String id, VerifyRequestDTO req, String approvedName){
        Project project =  getProject(id);
        if(!project.getStatus().equals(ProjectStatus.VERIFIED)){
            throw new RuntimeException("Project is no ready for approved");
        }
        project.setApprovedBy(approvedName);

        if(req.isApproved()){
            project.setStatus(ProjectStatus.APPROVED);
        }
        else{
            project.setStatus(ProjectStatus.REJECTED_BY_GOV);
        }

        project.setUpdatedAt(LocalDateTime.now());
        return  projectRepository.save(project);
    }



}






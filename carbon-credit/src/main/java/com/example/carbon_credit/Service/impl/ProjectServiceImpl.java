package com.example.carbon_credit.Service.impl;

import com.example.carbon_credit.Model.Project;
import com.example.carbon_credit.Repository.ProjectRepository;
import com.example.carbon_credit.Service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ProjectServiceImpl implements ProjectService {
    private final ProjectRepository projectRepository;

    @Autowired
    public ProjectServiceImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
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


}






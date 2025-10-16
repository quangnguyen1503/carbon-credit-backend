package com.example.carbon_credit.Service;

import com.example.carbon_credit.Model.Project;

import java.util.List;

public interface ProjectService {

    Project saveProject(Project project);

    List<Project> getAllProject();
}

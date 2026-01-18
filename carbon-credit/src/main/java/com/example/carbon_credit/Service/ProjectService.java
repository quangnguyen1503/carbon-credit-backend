package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.ApprovedRequestDTO;
import com.example.carbon_credit.DTO.ProjectResponse;
import com.example.carbon_credit.DTO.ProjectWithCreditDTO;
import com.example.carbon_credit.DTO.VerifyRequestDTO;
import com.example.carbon_credit.Entity.CarbonCredit;
import com.example.carbon_credit.Entity.Project;
import com.example.carbon_credit.constants.ProjectStatus;

import java.util.List;
import java.util.Optional;

public interface ProjectService {

    Project saveProject(Project project);
    List<Project> getAllProjectSubmited(String status);

    List<ProjectResponse> getMyProject(String userId);

    Project getProject(String id);
    Project VerifyProject(String id, VerifyRequestDTO req, String VerifyName);

    Project ApprovedProject(String id, ApprovedRequestDTO req, String VerifyName);

    List<Project>  getProjectByVerify(String status, String verifierRoleId);

    Optional<Project> getProjectDetail(String id );

    List<Project> getProjectsByStatuses(List<String> statuses);

    ProjectWithCreditDTO getProjectWithCredit(String projectId);
    Project getProjectById(String projectId);

    CarbonCredit getCarbonCreditByProjectId(String projectId);





//    Project RejectProject( String id , VerifyRequestDTO req, String RejectName);
}

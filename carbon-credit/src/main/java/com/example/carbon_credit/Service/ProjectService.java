package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.ProjectResponse;
import com.example.carbon_credit.DTO.VerifyRequestDTO;
import com.example.carbon_credit.Entity.Project;

import java.util.List;

public interface ProjectService {

    Project saveProject(Project project);
    List<ProjectResponse> getAllProjectSubmited(String status);

    List<ProjectResponse> getMyProject(String userId);


    Project getProject(String id);
    Project VerifyProject(String id, VerifyRequestDTO req, String VerifyName);

    Project ApprovedProject(String id, VerifyRequestDTO req, String VerifyName);







//    Project RejectProject( String id , VerifyRequestDTO req, String RejectName);
}

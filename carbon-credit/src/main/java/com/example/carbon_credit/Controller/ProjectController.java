package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.VerifyRequestDTO;
import com.example.carbon_credit.Entity.Project;
import com.example.carbon_credit.Service.AuthService;
import com.example.carbon_credit.Service.ProjectService;
import com.example.carbon_credit.constants.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    @Autowired
    private ProjectService projectService;
    @Autowired
    private  AuthService authService;

    @PostMapping("/save")
    public Project saveProject(@RequestBody Project project){
        return projectService.saveProject(project);
    }

    @GetMapping("allProject")
    public List<Project> getAllProject(){
        return projectService.getAllProject();
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verifyProject(
            @PathVariable String id,
            @RequestBody VerifyRequestDTO req,
            Principal principal
    ) {

        // Role check
        if (!authService.hasRole(principal.getName(), UserRole.VERIFIER)) {
            return ResponseEntity.status(403).body("You are not a verifier");
        }
        Project result = projectService.VerifyProject(id, req, principal.getName());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/approved")
    public ResponseEntity<?> approved(
            @PathVariable String id,
            @RequestBody VerifyRequestDTO req,
            Principal principal
    ){
        // Role check
        if (!authService.hasRole(principal.getName(), UserRole.GOVERNMENT)) {
            return ResponseEntity.status(403).body("You are not a goverment");
        }
        Project result = projectService.ApprovedProject(id, req, principal.getName());
        return ResponseEntity.ok(result);

    }




}
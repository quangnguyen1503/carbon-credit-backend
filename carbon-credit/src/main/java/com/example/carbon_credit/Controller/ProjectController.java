package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.ProjectUploadDto;
import com.example.carbon_credit.DTO.ProjectUploadResponse;
import com.example.carbon_credit.DTO.VerifyRequestDTO;
import com.example.carbon_credit.Entity.Project;
import com.example.carbon_credit.Service.AuthService;
import com.example.carbon_credit.Service.PinataService;
import com.example.carbon_credit.Service.ProjectService;
import com.example.carbon_credit.constants.ProjectStatus;
import com.example.carbon_credit.constants.UserRole;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    @Autowired
    private ProjectService projectService;
    @Autowired
    private  AuthService authService;

    @Autowired
    private PinataService pinataService;

    @PostMapping("/save")
    public Project saveProject(@RequestBody Project project){
        return projectService.saveProject(project);
    }

    @GetMapping("/ProjectSubmitted")
    public List<Project> getAllProjectSubmit(){
        return projectService.getAllProjectSubmited(ProjectStatus.SUBMITTED);

    }
    @GetMapping("/ProjectApproved")
    public List<Project> getAllProjectApproved(){
        return projectService.getAllProjectSubmited(ProjectStatus.APPROVED);
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

    /**
     * POST /api/projects/upload
     * @param dto @Valid DTO từ FE FormData
     */
    @PostMapping("/upload")
    public ResponseEntity<ProjectUploadResponse> uploadProjectData(@Valid @RequestBody ProjectUploadDto dto) { // @RequestBody cho JSON, hoặc dùng @ModelAttribute cho form
        ProjectUploadResponse response = new ProjectUploadResponse();
        response.setSuccess(false);

        try {
            // 1. Upload Image (optional)
            String imageUrl = null;
            if (dto.getImageFile() != null && !dto.getImageFile().isEmpty()) {
                imageUrl = pinataService.uploadFileToIPFS(dto.getImageFile(), "project-image");
            }

            // 2. Upload Document (PDF, optional)
            String docUrl = null;
            if (dto.getDocFile() != null && !dto.getDocFile().isEmpty()) {
                if (!dto.getDocFile().getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                    response.setMessage("Document must be PDF");
                    return ResponseEntity.badRequest().body(response);
                }
                docUrl = pinataService.uploadFileToIPFS(dto.getDocFile(), "project-doc");
            }

            // 3. Prepare & Upload Metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("projectName", dto.getProjectName());
            metadata.put("description", dto.getDescription());
            metadata.put("location", dto.getLocation());
            metadata.put("methodology", dto.getMethodology());
            metadata.put("vintage", dto.getVintage());
            metadata.put("price", dto.getPrice());
            metadata.put("receiver", dto.getReceiver());
            metadata.put("image", imageUrl);
            metadata.put("document", docUrl);
            metadata.put("timestamp", Instant.now().toString());

            String metadataUri = pinataService.uploadJsonToIPFS(metadata, "project-metadata");
            if (metadataUri == null) {
                response.setMessage("Metadata upload failed");
                return ResponseEntity.internalServerError().body(response);
            }

            // Success response
            response.setImageUrl(imageUrl);
            response.setDocUrl(docUrl);
            response.setMetadataHash(metadataUri.replace("ipfs://", ""));
            response.setFullMetadataUri(metadataUri);
            response.setSuccess(true);
            response.setMessage("Upload successful");

//            log.info("Project upload success: metadataHash={}", response.getMetadataHash());
            return ResponseEntity.ok(response);

        } catch (IOException e) {
//            log.error("IO Error during upload: {}", e.getMessage(), e);
            response.setMessage("File read error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
//            log.error("Unexpected error: {}", e.getMessage(), e);
            response.setMessage("Upload failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }




}
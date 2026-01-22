package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.*;
import com.example.carbon_credit.Entity.CarbonCredit;
import com.example.carbon_credit.Entity.Project;
import com.example.carbon_credit.Service.*;
import com.example.carbon_credit.constants.ProjectStatus;
import com.example.carbon_credit.constants.UserRole;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    @Autowired
    private ProjectService projectService;
    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private TradingService tradingService;

    @Autowired
    private PinataService pinataService;

    @PostMapping("/save")
    public Project saveProject(@RequestBody Project project) {
        return projectService.saveProject(project);
    }

    @GetMapping("/{id}")
    public Optional<Project> getProject(@PathVariable String id) {
        return projectService.getProjectDetail(id);
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<ProjectResponse> getProjectDetail(@PathVariable("id") String projectId) {
        return projectService.getDetailProject(projectId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }



    @GetMapping("/verifier-project")
    public List<Project> getProjectsByStatus(@RequestParam String status, Principal principal) {
        String userId = principal.getName();
        String verifierRoleId = userService.getVerifierRoleIdByUsername(userId);

        return projectService.getProjectByVerify(status, verifierRoleId);
    }

    @GetMapping("/MyProject")
    public List<ProjectResponse> getMyProject(Principal principal) {
        return projectService.getMyProject(principal.getName());
    }

    @GetMapping("/ProjectVerified")
    public List<Project> getAllProjectApproved() {
        return projectService.getAllProjectSubmited(ProjectStatus.VERIFIED);
    }
    @GetMapping("/ProjectApproved")
    public List<ProjectResponse> getProject() {
        return projectService.getProjectApproved(ProjectStatus.APPROVED);
    }

    @GetMapping("/ProjectApproved")
    public List<Project> getAllProjectApprovedd() {
        return projectService.getAllProjectSubmited(ProjectStatus.APPROVED);
    }

    @GetMapping("/processed-project")
    public List<Project> getProcessedProject() {
        return projectService.getProjectsByStatuses(List.of(ProjectStatus.APPROVED, ProjectStatus.REJECTED_BY_GOV));
    }


    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verifyProject(@PathVariable String id, @RequestBody VerifyRequestDTO req, Principal principal) {

        // Role check
        if (!authService.hasRole(principal.getName(), UserRole.VERIFIER)) {
            return ResponseEntity.status(403).body("You are not a verifier");
        }
        Project result = projectService.VerifyProject(id, req, principal.getName());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/approved")
    public ResponseEntity<?> approved(@PathVariable String id, @RequestBody ApprovedRequestDTO req, Principal principal) {
        if (!authService.hasRole(principal.getName(), UserRole.GOVERNMENT)) {
            return ResponseEntity.status(403).body("You are not a government");
        }

        Project result = projectService.ApprovedProject(id, req, principal.getName());

        return ResponseEntity.ok(result);
    }


    /**
     * POST /api/projects/upload
     *
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
            metadata.put("vintage", dto.getVintage());
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

    @GetMapping("/{projectId}/listed")
    public ResponseEntity<?> checkProjectListed(@PathVariable String projectId) {
        try {
            log.info("🔍 Checking if project is listed: {}", projectId);
            boolean isListed = tradingService.hasActiveOrderBook(projectId);

            return ResponseEntity.ok(Map.of("projectId", projectId, "isListed", isListed));
        } catch (Exception e) {
            log.error(" Failed to check project status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error", "message", "Failed to check project status"));
        }
    }

    @GetMapping("/marketplace")
    public ResponseEntity<?> getMarketplaceProjects() {
        try {
            log.info("📋 Getting marketplace projects (APPROVED by government)");

            // Get all APPROVED projects
            List<Project> projects = projectService.getProjectsByStatuses(List.of(ProjectStatus.APPROVED));

            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            log.error("❌ Failed to get marketplace projects: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error", "message", "Failed to retrieve marketplace projects"));
        }
    }

    /**
     * Get orderbook cho một project
     */
    @GetMapping("/{projectId}/orderbook")
    public ResponseEntity<?> getProjectOrderBook(@PathVariable String projectId) {
        try {
            log.info("📊 Getting orderbook for project: {}", projectId);

            CarbonCredit credit = projectService.getCarbonCreditByProjectId(projectId);
            if (credit == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not Found", "message", "Carbon credit not found for this project"));
            }

            String creditId = String.valueOf(credit.getTokenId());
            Map<String, Object> orderbook = tradingService.getOrderBookSnapshot(creditId);

            if (orderbook == null || orderbook.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not Found", "message", "OrderBook not found. Project may not be listed yet."));
            }

            return ResponseEntity.ok(orderbook);

        } catch (Exception e) {
            log.error("❌ Failed to get orderbook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error", "message", "Failed to retrieve orderbook"));
        }
    }

    @GetMapping("/{projectId}/trading-status")
    public ResponseEntity<?> getTradingStatus(@PathVariable String projectId) {
        try {
            log.info("🔍 Checking trading status for project: {}", projectId);

            // Check if project exists
            Optional<Project> projectOpt = projectService.getProjectDetail(projectId);
            if (projectOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not Found", "message", "Project not found"));
            }

            Project project = projectOpt.get();

            // Check if has carbon credit
            CarbonCredit credit = projectService.getCarbonCreditByProjectId(projectId);

            if (credit == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not Found", "message", "Carbon credit not found"));
            }

            Long tokenId = credit.getTokenId();
            Long issuedAmount = credit.getIssueAmount();
            Long retiredAmount = credit.getRetiredAmount();
            Long availableAmount = issuedAmount - retiredAmount;

            boolean isMinted = (issuedAmount != null && issuedAmount > 0);
            boolean canTrade = isMinted && availableAmount > 0;

            boolean hasOrderBook = false;
            if (isMinted && tokenId != null) {
                String creditId = String.valueOf(tokenId);
                hasOrderBook = tradingService.hasActiveOrderBook(creditId);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("projectId", projectId);
            response.put("projectName", project.getName());
            response.put("tokenId", tokenId);
            response.put("isMinted", isMinted);
            response.put("issueAmount", issuedAmount);
            response.put("availableAmount", availableAmount);
            response.put("hasOrderBook", hasOrderBook);
            response.put("canTrade", canTrade);

            // Add reason if cannot trade
            if (!canTrade) {
                if (!isMinted) {
                    response.put("reason", "Credits not minted yet (issueAmount = 0)");
                } else if (availableAmount <= 0) {
                    response.put("reason", "No credits available");
                }
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to check trading status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error", "message", "Failed to check trading status"));
        }
    }


}
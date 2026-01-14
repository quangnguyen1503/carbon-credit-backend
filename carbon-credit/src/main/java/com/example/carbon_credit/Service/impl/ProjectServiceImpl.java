package com.example.carbon_credit.Service.impl;

import com.example.carbon_credit.DTO.ApprovedRequestDTO;
import com.example.carbon_credit.DTO.ProjectResponse;
import com.example.carbon_credit.DTO.ProjectWithCreditDTO;
import com.example.carbon_credit.DTO.VerifyRequestDTO;
import com.example.carbon_credit.Entity.CarbonCredit;
import com.example.carbon_credit.Entity.Project;
import com.example.carbon_credit.Entity.User;
import com.example.carbon_credit.Repository.CarbonCreditRepository;
import com.example.carbon_credit.Repository.OrderRepository;
import com.example.carbon_credit.Repository.ProjectRepository;
import com.example.carbon_credit.Repository.UserRepository;
import com.example.carbon_credit.Service.MailService;
import com.example.carbon_credit.Service.ProjectService;
import com.example.carbon_credit.constants.ProjectStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectServiceImpl implements ProjectService {
    private final ProjectRepository projectRepository;
    @Autowired
    private MailService mailService;


    @Autowired
    private UserRepository userRepository;

    @Autowired
    CarbonCreditRepository carbonCreditRepository;

    @Autowired
    OrderRepository orderRepository;


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

        if (project.getId() == null || project.getId().trim().isEmpty()) {
            project.setId(UUID.randomUUID().toString());  // Generate UUID (unique string)
            System.out.println("Generated ID: " + project.getId());  // Debug log
        }

        // Set dates nếu null (optional, nếu DB default không có)
        if (project.getCreatedAt() == null) {
            project.setCreatedAt(LocalDateTime.now());
        }
        if (project.getUpdatedAt() == null) {
            project.setUpdatedAt(LocalDateTime.now());
        }

        if (project.getDescription() == null || project.getDescription().trim().isEmpty()) {
            project.setDescription("No description provided");  // Hoặc "" nếu DB chấp nhận empty string
            System.out.println("Set default des = 'No description provided'");
        }

        // Defaults cho nullable fields (nếu cần)
        if (project.getVerifiedBy() == null) {
            project.setVerifiedBy(null);
        }
        if (project.getApprovedBy() == null) {
            project.setApprovedBy(null);
        }

        // Validate not null fields (optional, để tránh lỗi sau)
        if (project.getStatus() == null || project.getStatus().trim().isEmpty()) {
            project.setStatus("SUBMITTED");  // Default từ constants
        }


        // Save và return
        Project saved = projectRepository.save(project);
        System.out.println("Saved Project ID: " + saved.getId());  // Debug
        return saved;
    }

    @Override
    public List<Project> getAllProjectSubmited(String status) {
        return projectRepository.findByStatus(status);
    }

    public List<Project> getProjectsByStatuses(List<String> statuses) {
        return projectRepository.findByStatusIn(statuses);
    }



    @Override
    public List<Project> getProjectByVerify(String status, String verifierRoleId) {
        return projectRepository.findByStatusAndVerifierRoleId(status, verifierRoleId);
    }

    @Override
    public Optional<Project> getProjectDetail(String id) {
        return projectRepository.findById(id);
    }

    @Override
    public List<ProjectResponse> getMyProject(String userId) {
        return projectRepository.findAllByOwnerId(userId);
    }


    // Trong ProjectServiceImpl.java
    @Override
    public Project VerifyProject(String id, VerifyRequestDTO req, String verifyName) {  // Đổi tên param cho rõ: verifyName thay VerifyName
        Project project = getProject(id);
        if (!project.getStatus().equals(ProjectStatus.SUBMITTED)) {
            throw new RuntimeException("Project is not ready for verification");
        }
        User owner = userRepository.findById(project.getOwnerId())
                .orElseThrow(() -> new RuntimeException("Owner not found"));

        String ownerEmail = owner.getEmail();

        // Set verifiedBy = verifier's address (hoặc name nếu cần)
        project.setVerifiedBy(verifyName);  // ← THÊM DÒNG NÀY!

        if (req.isApproved()) {
            project.setStatus(ProjectStatus.VERIFIED);
            project.setExpectedCredits(req.getExpectedCredits());

            mailService.sendVerifyProject(ownerEmail, project.getName());
        } else {
            project.setStatus(ProjectStatus.REJECTED_BY_VERIFIER);
            mailService.sendRejectProject(ownerEmail,project.getName(),req.getReason());

        }


        project.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(project);
    }
    @Override
    public Project ApprovedProject(String id, ApprovedRequestDTO req, String approvedName){
        Project project =  getProject(id);
        if(!project.getStatus().equals(ProjectStatus.VERIFIED)){
            throw new RuntimeException("Project is no ready for approved");
        }
        project.setApprovedBy(approvedName);

        User owner = userRepository.findById(project.getOwnerId())
                .orElseThrow(() -> new RuntimeException("Owner not found"));

        String ownerEmail = owner.getEmail();

        if(req.isApproved()){
            project.setStatus(ProjectStatus.APPROVED);
            project.setNftTokenId(req.getNftTokenId());
            CarbonCredit carbonCredit = CarbonCredit.builder()
                    .id(UUID.randomUUID().toString())
                    .tokenId(req.getTokenId())
                    .projectId(project.getId())
                    .totalAmount(project.getExpectedCredits())
                    .retiredAmount(0)
                    .issueAmount(0)
                    .build();
            carbonCreditRepository.save(carbonCredit);
            mailService.sendApproveProject(ownerEmail, project.getName());
        }
        else{
            project.setStatus(ProjectStatus.REJECTED_BY_GOV);
            mailService.sendRejectProject(ownerEmail,project.getName(),req.getReason());
        }

        project.setUpdatedAt(LocalDateTime.now());
        return  projectRepository.save(project);
    }


    public List<ProjectWithCreditDTO> getAllProjectsWithCredits() {
        List<Project> projects = projectRepository.findAll();

        return projects.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ProjectWithCreditDTO> getProjectsByOwner(String userId) {
        List<Project> projects = projectRepository.findByOwnerId(userId);

        return projects.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ProjectWithCreditDTO getProjectWithCredit(String projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return null;

        return convertToDTO(project);
    }

    public Project getProjectById(String projectId) {
        return projectRepository.findById(projectId).orElse(null);
    }

    public CarbonCredit getCarbonCreditByProjectId(String projectId) {
        return carbonCreditRepository.findByProjectId(projectId).orElse(null);
    }

    private ProjectWithCreditDTO convertToDTO(Project project) {
        ProjectWithCreditDTO dto = ProjectWithCreditDTO.builder()
                .id(project.getId())
                .name(project.getName())
                .vintage(project.getVintage())
                .ownerId(project.getOwnerId())
                .type(project.getType())
                .location(project.getLocation())
                .description(project.getDescription())
                .ipfsHash(project.getIpfsHash())
                .status(project.getStatus())
                .createdAt(project.getCreatedAt())
                .build();

        // Get carbon credit info if exists
        CarbonCredit credit = carbonCreditRepository.findByProjectId(project.getId()).orElse(null);
        if (credit != null) {
            dto.setTokenId(credit.getTokenId());
            dto.setTotalAmount(credit.getTotalAmount());
            dto.setIssueAmount(credit.getIssueAmount());
            dto.setRetiredAmount(credit.getRetiredAmount());
            dto.setAvailableAmount(credit.getIssueAmount() - credit.getRetiredAmount());

            // Check if has active orders (is listed)
            String creditId = String.valueOf(credit.getTokenId());
            boolean hasOrders = !orderRepository.findByCreditIdAndStatus(creditId, "OPEN").isEmpty();
            dto.setIsListed(hasOrders);
            dto.setHasOrderBook(hasOrders);
        } else {
            dto.setIsListed(false);
            dto.setHasOrderBook(false);
        }

        return dto;
    }


}






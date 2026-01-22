package com.example.carbon_credit.Service.impl;

import com.example.carbon_credit.DTO.*;
import com.example.carbon_credit.Entity.CarbonCredit;
import com.example.carbon_credit.Entity.ProcessedTransaction;
import com.example.carbon_credit.Entity.Project;
import com.example.carbon_credit.Entity.User;
import com.example.carbon_credit.Repository.*;
import com.example.carbon_credit.Service.MailService;
import com.example.carbon_credit.Service.ProjectService;
import com.example.carbon_credit.Util.BlockchainHelper;
import com.example.carbon_credit.constants.ProjectStatus;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
@Slf4j
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
    private ProcessedTransactionRepository processedTransactionRepository;


    private final ConcurrentHashMap<String, Object> walletLocks = new ConcurrentHashMap<>();


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

    @Override
    public Optional<ProjectResponse> getDetailProject(String projectId) {
        return projectRepository.findProjectResponseById(projectId);
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



    @Transactional
    public void handleProjectApproved(BlockchainEventDTO event) {
        try {

            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
                log.warn("⚠️ Transaction {} already processed. Skipping.", event.getTransactionHash());
                return;
            }

            // 2. Trích xuất dữ liệu từ sự kiện
            // Lưu ý: Tùy vào cách bạn viết helper extract, hãy điều chỉnh index
            // nftTokenId và creditTokenId là 'indexed' nên nằm trong Topics
            BigInteger nftTokenId = BlockchainHelper.extractUint256FromTopic(event, 1); // Indexed param 1
            BigInteger creditTokenId = BlockchainHelper.extractUint256FromTopic(event, 2); // Indexed param 2

            // uuid là string và nằm trong phần 'data' (non-indexed)
            // Nếu bạn chưa có hàm extractString, bạn cần decode phần data của event
            List<TypeReference<Type>> params = Arrays.asList(
                    (TypeReference) new TypeReference<Utf8String>() {}, // 0. orderId
                    (TypeReference) new TypeReference<Address>() {},    // 1. user
                    (TypeReference) new TypeReference<Address>() {},
                    (TypeReference) new TypeReference<Address>() {}

            );

            List<Type> decoded = BlockchainHelper.decodeAnyData(event.getData(), params);
            String projectId = (String) decoded.get(0).getValue();
            String approvedBY = (String) decoded.get(3).getValue();




            if (projectId == null || nftTokenId == null) {
                log.error("❌ Invalid PROJECT_APPROVED event data: uuid or nftTokenId is null");
                return;
            }

            // 3. Sử dụng Lock để tránh Race Condition (nếu cần)
            Object lock = walletLocks.computeIfAbsent(projectId, k -> new Object());

            synchronized (lock) {
                log.info("🌿 Processing Blockchain Event: ProjectApproved for UUID: {}", projectId);

                // 4. Tìm Project trong Database bằng UUID (id)
                Project project = projectRepository.findById(projectId)
                        .orElseThrow(() -> new RuntimeException("Project not found in DB: " + projectId));

                // Chỉ cập nhật nếu trạng thái hiện tại chưa phải là APPROVED (phòng hờ sync chậm)
                if (!ProjectStatus.APPROVED.equals(project.getStatus())) {

                    // 5. Cập nhật thông tin Project
                    project.setStatus(ProjectStatus.APPROVED);
                    project.setNftTokenId(nftTokenId.longValue());
                    project.setUpdatedAt(LocalDateTime.now());
                    project.setOnchainHash(event.getTransactionHash());
                    project.setApprovedBy(approvedBY);



                    // 6. Tạo hoặc Cập nhật CarbonCredit (Tài sản giao dịch)
                    // Kiểm tra xem đã tồn tại chưa để tránh lỗi Duplicate
                    CarbonCredit carbonCredit = carbonCreditRepository.findByProjectId(project.getId())
                            .orElse(new CarbonCredit());

                    carbonCredit.setId(UUID.randomUUID().toString()); // Nếu tạo mới
                    carbonCredit.setTokenId(creditTokenId.longValue());
                    carbonCredit.setProjectId(project.getId());
                    carbonCredit.setTotalAmount(project.getExpectedCredits());
                    carbonCredit.setRetiredAmount(0);
                    carbonCredit.setIssueAmount(0);

                    carbonCreditRepository.save(carbonCredit);
                    projectRepository.save(project);

                    log.info("✅ Project {} sync-ed with Blockchain. NFT ID: {}, Credit ID: {}",
                            projectId, nftTokenId, creditTokenId);
                }

                // 7. Lưu vết giao dịch blockchain đã xử lý
                ProcessedTransaction processedTx = ProcessedTransaction.builder()
                        .txHash(event.getTransactionHash())
                        .eventType(event.getEventType())
                        .processedAt(LocalDateTime.now())
                        .build();
                processedTransactionRepository.save(processedTx);
            }

        } catch (Exception e) {
            log.error("❌ Error handling PROJECT_APPROVED: {}", e.getMessage(), e);
            throw e; // Ném lỗi để Transactional thực hiện rollback nếu có lỗi DB
        }
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


    private String extractAddress(BlockchainEventDTO event, int topicIndex) {
        try {
            if (event.getTopics().size() <= topicIndex) {
                return null;
            }
            String topic = event.getTopics().get(topicIndex);
            Address address = TypeDecoder.decodeAddress(topic);
            return address.getValue();
        } catch (Exception e) {
            log.error("❌ Failed to extract address from topic {}: {}", topicIndex, e.getMessage());
            return null;
        }
    }


}






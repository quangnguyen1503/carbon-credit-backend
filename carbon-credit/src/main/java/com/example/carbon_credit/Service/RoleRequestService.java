package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.BlockchainEventDTO;
import com.example.carbon_credit.DTO.RoleRequestDTO;
import com.example.carbon_credit.Entity.*;
import com.example.carbon_credit.Repository.*;
import com.example.carbon_credit.Util.BlockchainHelper;
import com.example.carbon_credit.constants.RoleRequestStatus;
import com.example.carbon_credit.constants.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RoleRequestService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRequestRepository roleRequestRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private VerifierRoleRepository verifierRoleRepository;

    @Autowired
    private MailService mailService;

    @Autowired
    private ProcessedTransactionRepository processedTransactionRepository;

    @Autowired
    private WsService wsService;

    private final ConcurrentHashMap<String, Object> walletLocks = new ConcurrentHashMap<>();

    public List<RoleRequest> getRequestConfirm() {
        return roleRequestRepository.findByStatus(RoleRequestStatus.CONFIRMED);
    }

    @Transactional
    public void requestRole(String userId, RoleRequestDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getEmail().isEmpty()) {
            throw new RuntimeException("You must update your email! ");
        }

        Optional<RoleRequest> existing = roleRequestRepository.findByUserIdAndStatus(userId, RoleRequestStatus.PENDING);
        if (existing.isPresent()) {
            throw new RuntimeException("You already have a pending role request.");
        }

        if (UserRole.VERIFIER.equals(dto.getRequestedRole())) {
            if (dto.getVerifierRoleId() == null) {
                throw new RuntimeException("Verifier must select an organization");
            }
        } else {
            if (dto.getVerifierRoleId() != null) {
                throw new RuntimeException("Only verifier can select organization");
            }
        }

        String token = UUID.randomUUID().toString();

        RoleRequest req = new RoleRequest();
        req.setUserId(userId);
        req.setRequestedRole(dto.getRequestedRole());
        req.setVerifierRoleId(dto.getVerifierRoleId());
        req.setReason(dto.getReason());
        req.setStatus(RoleRequestStatus.PENDING);
        req.setEmailToken(token);
        req.setTokenExpiredAt(LocalDateTime.now().plusMinutes(15));

        roleRequestRepository.save(req);

        String confirmLink = "http://localhost:8080/api/role-request/confirm?token=" + token;

        mailService.sendConfirmRoleEmail(user.getEmail(), dto.getRequestedRole(), confirmLink);
    }

    @Transactional
    public void confirmRoleRequest(String token) {
        RoleRequest req = roleRequestRepository.findByEmailToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (req.getTokenExpiredAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token expired");
        }

        if (!req.getStatus().equals(RoleRequestStatus.PENDING)) {
            throw new RuntimeException("Request already processed.");
        }

        req.setStatus(RoleRequestStatus.CONFIRMED);
        roleRequestRepository.save(req);
    }

    @Transactional
    public void approveRoleRequest(String requestId) {
        Optional<RoleRequest> optionalRoleRequest = roleRequestRepository.findById(requestId);
        RoleRequest req = optionalRoleRequest.orElseThrow(() -> new RuntimeException("Req not found"));

        if (req.getStatus().equals(RoleRequestStatus.APPROVE)) {
            throw new RuntimeException("Request already approved.");
        }

        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setRoleId(req.getRequestedRole());

        if (UserRole.VERIFIER.equals(req.getRequestedRole())) {
            user.setVerifierRoleId(req.getVerifierRoleId());
        }
        req.setStatus(RoleRequestStatus.APPROVE);

        mailService.sendApproveResult(user.getEmail(), req.getRequestedRole());
    }

    @Transactional
    public void rejectRoleRequest(String requestId, String rejectReason) {
        Optional<RoleRequest> optionalReq = roleRequestRepository.findById(requestId);
        RoleRequest req = optionalReq.orElseThrow(() -> new RuntimeException("Request not found"));

        if (!req.getStatus().equals(RoleRequestStatus.CONFIRMED) && !req.getStatus().equals(RoleRequestStatus.PENDING)) {
            throw new RuntimeException("Request cannot be rejected.");
        }

        req.setStatus(RoleRequestStatus.RREJECT);
        roleRequestRepository.save(req);

        User user = userRepository.findById(req.getUserId()).orElseThrow();
        mailService.sendRejectRole(user.getEmail(), req.getRequestedRole());
    }

    @Transactional
    public void addRoleDirectly(String userid, String roleName, String verifierRoleId) {
        User user = userRepository.findById(userid)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        user.setRoleId(roleName);

        if (UserRole.VERIFIER.equals(roleName) && verifierRoleId != null) {
            user.setVerifierRoleId(verifierRoleId);
        } else {
            user.setVerifierRoleId(null);
        }

        mailService.sendApprovedRole(user.getEmail(), roleName);
    }

    @Transactional
    public void removeRole(String userid) {
        User user = userRepository.findById(userid)
                .orElseThrow(() -> new RuntimeException("user not found"));
        user.setRoleId("USER");
        user.setVerifierRoleId(null);

        mailService.sendRemovetRole(user.getEmail());
    }

    // ================= BLOCKCHAIN HANDLERS (USING HELPER) =================

    @Transactional
    public void handleAdminAdded(BlockchainEventDTO event) {
        try {
            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
                log.warn("⚠️ Transaction {} already processed.", event.getTransactionHash());
                return;
            }

            // ✅ Sử dụng Helper
            String userAddress = BlockchainHelper.extractAddressFromTopic(event, 1);
            if (userAddress == null) return;

            Object lock = walletLocks.computeIfAbsent(userAddress.toLowerCase(), k -> new Object());
            synchronized (lock) {
                addRoleDirectly(userAddress, "ADMIN", null);
                saveProcessedTx(event);

                wsService.notify(
                        "Thêm quyền Admin thành công",
                        String.format("Bạn đã được cấp quyền Admin cho địa chỉ ví: %s.", userAddress),
                        "SUCCESS",
                        Arrays.asList("ADMIN"),
                        null
                );
            }
        } catch (Exception e) {
            log.error("❌ Error handling ADMIN ADDED: {}", e.getMessage(), e);
            throw e;
        }
    }

        @Transactional
        public void handleGovernmentAdded(BlockchainEventDTO event) {
            try {
                if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
                    log.warn("⚠️ Transaction {} already processed.", event.getTransactionHash());
                    return;
                }

                // ✅ Sử dụng Helper
                String userAddress = BlockchainHelper.extractAddressFromTopic(event, 1);
                if (userAddress == null) return;

                Object lock = walletLocks.computeIfAbsent(userAddress.toLowerCase(), k -> new Object());
                synchronized (lock) {
                    addRoleDirectly(userAddress, "GOVERNMENT", null);
                    saveProcessedTx(event);

                    wsService.notify(
                            "Thêm quyền GOVERNMENT thành công",
                            String.format("Địa chỉ ví %s đã được cấp quyền GOVERNMENT.", userAddress),
                            "SUCCESS",
                            Arrays.asList("ADMIN", "GOVERNMENT"),
                            null
                    );
                }
            } catch (Exception e) {
                log.error("❌ Error handling GOVERNMENT ADDED: {}", e.getMessage(), e);
                throw e;
            }
        }

    @Transactional
    public void handleVerifierAdded(BlockchainEventDTO event) {
        try {
            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) {
                log.warn("⚠️ Transaction {} already processed.", event.getTransactionHash());
                return;
            }

            // ✅ Sử dụng Helper để lấy Address và Organization Name
            String userAddress = BlockchainHelper.extractAddressFromTopic(event, 1);
            String orgName = BlockchainHelper.extractStringFromData(event);

            if (userAddress == null) {
                log.error("❌ Invalid VERIFIER ADDED event (Address null)");
                return;
            }

            // Logic tìm Verifier Role ID
            String verifierRoleId = null;
            if (orgName != null && !orgName.isEmpty()) {
                VerifierRole verifierRole = verifierRoleRepository.findByOrganizationName(orgName);
                if (verifierRole != null) {
                    verifierRoleId = verifierRole.getId();
                } else {
                    log.error("❌ Organization '{}' not found in DB", orgName);
                }
            }

            Object lock = walletLocks.computeIfAbsent(userAddress.toLowerCase(), k -> new Object());
            synchronized (lock) {
                addRoleDirectly(userAddress, "VERIFIER", verifierRoleId);
                saveProcessedTx(event);

                wsService.notify(
                        "Thêm quyền VERIFIER thành công",
                        String.format("Địa chỉ ví %s được cấp quyền VERIFIER (%s).", userAddress, orgName),
                        "SUCCESS",
                        Arrays.asList("ADMIN", "VERIFIER"),
                        null
                );
            }
        } catch (Exception e) {
            log.error("❌ Error handling VERIFIER ADDED: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void handleAdminRemoved(BlockchainEventDTO event) {
        try {
            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) return;

            String userAddress = BlockchainHelper.extractAddressFromTopic(event, 1);
            if (userAddress == null) return;

            Object lock = walletLocks.computeIfAbsent(userAddress.toLowerCase(), k -> new Object());
            synchronized (lock) {
                removeRole(userAddress);
                saveProcessedTx(event);

                wsService.notify("Xóa quyền ADMIN", "Địa chỉ " + userAddress + " bị xóa quyền ADMIN.", "SUCCESS", Arrays.asList("ADMIN"), null);
            }
        } catch (Exception e) {
            log.error("❌ Error handling ADMIN REMOVED", e);
            throw e;
        }
    }

    @Transactional
    public void handleGovernmentRemoved(BlockchainEventDTO event) {
        try {
            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) return;

            String userAddress = BlockchainHelper.extractAddressFromTopic(event, 1);
            if (userAddress == null) return;

            Object lock = walletLocks.computeIfAbsent(userAddress.toLowerCase(), k -> new Object());
            synchronized (lock) {
                removeRole(userAddress);
                saveProcessedTx(event);

                wsService.notify("Xóa quyền GOVERNMENT", "Địa chỉ " + userAddress + " bị xóa quyền GOVERNMENT.", "SUCCESS", Arrays.asList("ADMIN"), userAddress);
            }
        } catch (Exception e) {
            log.error("❌ Error handling GOVERNMENT REMOVED", e);
            throw e;
        }
    }

    @Transactional
    public void handleVerifierRemoved(BlockchainEventDTO event) {
        try {
            if (processedTransactionRepository.existsByTxHash(event.getTransactionHash())) return;

            String userAddress = BlockchainHelper.extractAddressFromTopic(event, 1);
            if (userAddress == null) return;

            Object lock = walletLocks.computeIfAbsent(userAddress.toLowerCase(), k -> new Object());
            synchronized (lock) {
                removeRole(userAddress);
                saveProcessedTx(event);

                wsService.notify("Xóa quyền VERIFIER", "Địa chỉ " + userAddress + " bị xóa quyền VERIFIER.", "SUCCESS", Arrays.asList("ADMIN"), userAddress);
            }
        } catch (Exception e) {
            log.error("❌ Error handling VERIFIER REMOVED", e);
            throw e;
        }
    }

    // Hàm phụ trợ để lưu processed tx cho gọn code
    private void saveProcessedTx(BlockchainEventDTO event) {
        ProcessedTransaction processedTx = ProcessedTransaction.builder()
                .txHash(event.getTransactionHash())
                .eventType(event.getEventType())
                .processedAt(LocalDateTime.now())
                .build();
        processedTransactionRepository.save(processedTx);
    }
}
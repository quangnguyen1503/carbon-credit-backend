package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.RoleRequestDTO;
import com.example.carbon_credit.Entity.RoleRequest;
import com.example.carbon_credit.Entity.User;
import com.example.carbon_credit.Repository.RoleRequestRepository;
import com.example.carbon_credit.Repository.UserRepository;
import com.example.carbon_credit.constants.RoleRequestStatus;  // Giả sử enum: PENDING, CONFIRMED
import com.example.carbon_credit.constants.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RoleRequestService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRequestRepository roleRequestRepository;

    @Autowired
    private MailService mailService;


    public  List<RoleRequest> getRequestConfirm(){
        return roleRequestRepository.findByStatus(RoleRequestStatus.CONFIRMED);
    }

    @Transactional
    public void requestRole(String userId, RoleRequestDTO dto) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if(user.getEmail().isEmpty() ){
            throw new RuntimeException("You must update your email! ");

        }

        Optional<RoleRequest> existing = roleRequestRepository.findByUserIdAndStatus(userId, RoleRequestStatus.PENDING);
        if (existing.isPresent()) {
            throw new RuntimeException("You already have a pending role request.");
        }

        String token = UUID.randomUUID().toString();

        RoleRequest req = new RoleRequest();
        req.setUserId(userId);
        req.setRequestedRole(dto.getRequestedRole());
        req.setReason(dto.getReason());
        req.setStatus(RoleRequestStatus.PENDING);  // Dùng enum nếu có
        req.setEmailToken(token);
        req.setTokenExpiredAt(LocalDateTime.now().plusMinutes(15));

        roleRequestRepository.save(req);

        String confirmLink = "http://localhost:8080/api/role-request/confirm?token=" + token;

        mailService.sendConfirmRoleEmail(
                user.getEmail(),
                dto.getRequestedRole(),
                confirmLink
        );
    }

    @Transactional
    public void confirmRoleRequest(String token) {
        RoleRequest req = roleRequestRepository.findByEmailToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (req.getTokenExpiredAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token expired");
        }

        // Check not already confirmed
        if (!req.getStatus().equals(RoleRequestStatus.PENDING)) {
            throw new RuntimeException("Request already processed.");
        }

        req.setStatus(RoleRequestStatus.CONFIRMED);
        roleRequestRepository.save(req);
    }

    @Transactional
    public void approveRoleRequest(String requestId){
        Optional<RoleRequest> optionalRoleRequest = roleRequestRepository.findById(requestId);
        RoleRequest req = optionalRoleRequest.orElseThrow(() -> new RuntimeException("Req not found"));

        if(req.getRequestedRole().equals(RoleRequestStatus.CONFIRMED)){
            throw new RuntimeException("Request must be confirmed before approve.");
        }
        req.setStatus(RoleRequestStatus.APPROVE);





        User user = userRepository.findById(req.getUserId()).orElseThrow();
        user.setRoleId(req.getRequestedRole());



        mailService.sendApproveResult(user.getEmail(),req.getRequestedRole());


    }
    @Transactional
    public void rejectRoleRequest(String requestId, String rejectReason) {  // Thêm reason optional
        Optional<RoleRequest> optionalReq = roleRequestRepository.findById(requestId);
        RoleRequest req = optionalReq.orElseThrow(() -> new RuntimeException("Request not found"));

        // Check status: Chỉ reject nếu CONFIRMED hoặc PENDING
        if (!req.getStatus().equals(RoleRequestStatus.CONFIRMED) && !req.getStatus().equals(RoleRequestStatus.PENDING)) {
            throw new RuntimeException("Request cannot be rejected.");
        }

        req.setStatus(RoleRequestStatus.RREJECT);
        // Optional: Set reason nếu entity có field reasonReject
        // req.setRejectReason(rejectReason);
        roleRequestRepository.save(req);

        // Gửi email reject
        User user = userRepository.findById(req.getUserId()).orElseThrow();
        mailService.sendRejectRole(user.getEmail(), req.getRequestedRole());
    }


}
package com.example.carbon_credit.Service;

import com.example.carbon_credit.Entity.RoleRequest;
import com.example.carbon_credit.Entity.User;
import com.example.carbon_credit.Repository.RoleRequestRepository;
import com.example.carbon_credit.Repository.UserRepository;
import com.example.carbon_credit.constants.RoleRequestStatus;

public class AdminService {
    RoleRequestRepository roleRequestRepository;
    UserRepository userRepository;

    MailService mailService;

    public void approveRole(String requestId) {

        RoleRequest req = roleRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setRoleId(req.getRequestedRole());
        userRepository.save(user);

        req.setStatus(RoleRequestStatus.APPROVE);
        roleRequestRepository.save(req);

        // 🔥 GỬI EMAIL THÔNG BÁO KẾT QUẢ
        mailService.sendApproveResult(user.getEmail(), user.getRoleId());
    }

}

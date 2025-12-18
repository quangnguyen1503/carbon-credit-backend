package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.RoleRequestDTO;
import com.example.carbon_credit.Service.MailService;
import com.example.carbon_credit.Service.RoleRequestService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;

import java.security.Principal;

@RestController
@RequestMapping("api/role-request")
public class RoleRequestController {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    @Autowired
    private RoleRequestService roleRequestService;

    @GetMapping("/confirm")
    @Transactional(readOnly = false)
    public ResponseEntity<?> confirmRoleRequest(@RequestParam String token) {

        try {
            roleRequestService.confirmRoleRequest(token);
            return ResponseEntity.ok("Email confirmed. Waiting for admin approval.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/request")
    public ResponseEntity<?> requestRole(@RequestBody RoleRequestDTO dto, Principal principal) {
        try {
            roleRequestService.requestRole(principal.getName(), dto);
            return ResponseEntity.ok("Role request sent. Please check your email.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new String[] {"Error: " + e.getMessage()});  // Match body array format nếu cần
        }
    }
    @PutMapping("/approve/{requestId}")
    public ResponseEntity<?> approveRoleRequest(@PathVariable String requestId) {
        try {
            roleRequestService.approveRoleRequest(requestId);
            return ResponseEntity.ok("Role approved and email sent.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Approve failed: " + e.getMessage());
        }
    }

    @PutMapping("/reject/{requestId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectRoleRequest(@PathVariable String requestId, @RequestParam(required = false) String reason) {
        try {
            roleRequestService.rejectRoleRequest(requestId,reason);
            return ResponseEntity.ok("Role rejected and email sent.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Reject failed: " + e.getMessage());
        }
    }
}
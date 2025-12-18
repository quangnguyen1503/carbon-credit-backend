package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.RetireRequestDTO;
import com.example.carbon_credit.Entity.RetireRequest;
import com.example.carbon_credit.Service.RetireRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/retire")
public class RetireRequestController {
    @Autowired
    RetireRequestService retireRequestService;

    @PostMapping("/request")
    public ResponseEntity<?> requestRetire(@RequestBody RetireRequestDTO dto, Principal principal) {
        try {
            RetireRequest result = retireRequestService.createRetireRequest(principal.getName(), dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);  // 201 cho create
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/approved-request/{requestId}")
    public ResponseEntity<?> approvedRequest(@PathVariable String requestId, Principal principal) {  // Sửa @PathVariable
        try {
            RetireRequest result = retireRequestService.approveRetire(requestId, principal.getName());
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/confirm-onchain/{requestId}")
    public ResponseEntity<?> confirmOnchainRequest(@PathVariable String requestId,  // Sửa @PathVariable
                                                   @RequestParam String txHash,
                                                   @RequestParam String nftTokenId) {  // Sửa @RequestParam
        try {
            RetireRequest result = retireRequestService.comfirmOnChain(requestId, txHash, nftTokenId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
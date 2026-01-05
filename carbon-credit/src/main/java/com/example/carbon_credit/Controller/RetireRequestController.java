package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.RetireRequestDTO;
import com.example.carbon_credit.Entity.RetireRequest;
import com.example.carbon_credit.Service.RetireRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.repository.query.Param;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/retire")
public class RetireRequestController {
    @Autowired
    RetireRequestService retireRequestService;


    @GetMapping("/all")
    public ResponseEntity<?> getAllRetireRecords(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return ResponseEntity.ok(
                retireRequestService.getRetireHistory(fromDate, toDate)
        );
    }

    @GetMapping("/getRetireWithStatus")
    public ResponseEntity<Page<RetireRequest>> getRetireWithStatus(
            @RequestParam(required = false) String status ,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Page<RetireRequest> requests = retireRequestService.getRetireWithPaginationAndSort(status,page,size,sortBy,sortDir);

        return ResponseEntity.ok(requests);
    }


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
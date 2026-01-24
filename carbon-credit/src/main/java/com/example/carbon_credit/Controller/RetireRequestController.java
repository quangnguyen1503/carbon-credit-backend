package com.example.carbon_credit.Controller;

import com.example.carbon_credit.Entity.Certificate;
import com.example.carbon_credit.Service.CertificateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/retire")
public class RetireRequestController {
    @Autowired
    CertificateService certificateService;

    @GetMapping("/all")
    public ResponseEntity<?> getAllRetireRecords(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(
                certificateService.getRetireHistory(fromDate, toDate));
    }

    @GetMapping("/getRetireWithStatus")
    public ResponseEntity<Page<Certificate>> getCerificateWithStatus(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Page<Certificate> certificates = certificateService.getCertificateWithPaginationAndSort(status, page, size,
                sortBy, sortDir);

        return ResponseEntity.ok(certificates);
    }

    // @PostMapping("/request")
    // public ResponseEntity<?> requestRetire(@RequestBody RetireRequestDTO dto,
    // Principal principal) {
    // try {
    // Certificate result =
    // certificateService.createRetireRequest(principal.getName(), dto);
    // return ResponseEntity.status(HttpStatus.CREATED).body(result); // 201 cho
    // create
    // } catch (RuntimeException e) {
    // return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    // }
    // }

    // @PutMapping("/approved-request/{requestId}")
    // public ResponseEntity<?> approvedRequest(@PathVariable String CertificateId,
    // Principal principal) { // Sửa @PathVariable
    // try {
    // Certificate result = certificateService.approveCertificate(CertificateId,
    // principal.getName());
    // return ResponseEntity.ok(result);
    // } catch (RuntimeException e) {
    // return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    // }
    // }

    // @PutMapping("/confirm-onchain/{requestId}")
    // public ResponseEntity<?> confirmOnchainRequest(@PathVariable String
    // requestId, // Sửa @PathVariable
    // @RequestParam String txHash,
    // @RequestParam String nftTokenId) { // Sửa @RequestParam
    // try {
    // Certificate result = certificateService.comfirmOnChain(requestId, txHash,
    // nftTokenId);
    // return ResponseEntity.ok(result);
    // } catch (RuntimeException e) {
    // return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    // }
    // }

}
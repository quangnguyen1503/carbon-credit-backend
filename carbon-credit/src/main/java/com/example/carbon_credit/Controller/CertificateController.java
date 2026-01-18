package com.example.carbon_credit.Controller;

import com.example.carbon_credit.DTO.CertificateDetailResponse;
import com.example.carbon_credit.DTO.CertificateResponse;
import com.example.carbon_credit.Service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    /**
     * Tạo yêu cầu retire nhiều token (OFF-CHAIN)
     * Status: PENDING
     */
    // @PostMapping("/retire")
    // public CertificateResponse retireMultiToken(
    // Principal principal,
    // @RequestBody RetireRequestDTO requestDTO
    // ) {
    // String userId = principal.getName();
    // return certificateService.retireMultiToken(userId, requestDTO);
    // }

    @GetMapping("/my-certificate")
    public List<CertificateResponse> getMyCertificate(Principal principal) {
        return certificateService.getMyCertificates(principal.getName());
    }

    @GetMapping("/certificate-detail")
    public CertificateDetailResponse getDetailCertificate(String certId) {
        return certificateService.getDetail(certId);
    }

}

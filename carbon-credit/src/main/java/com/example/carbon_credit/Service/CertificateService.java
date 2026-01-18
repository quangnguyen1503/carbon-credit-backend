package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.CertificateDetailResponse;
import com.example.carbon_credit.DTO.CertificateResponse;
import com.example.carbon_credit.DTO.RetireRequestDTO;
import com.example.carbon_credit.Entity.Certificate;
import com.example.carbon_credit.Entity.CertificateRecord;
import com.example.carbon_credit.Repository.CertificateRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CertificateService {
    @Autowired
    CertificateRepository certificateRepository;

    @Transactional
    public CertificateResponse retireMultiToken(String userId, RetireRequestDTO requestDTO){
        Certificate certificate = new Certificate();
        certificate.setId(UUID.randomUUID().toString());
        certificate.setUserId(userId);
        certificate.setOnchainTxHash(requestDTO.getOnchainTxHash());
        certificate.setNftTokenId(requestDTO.getNftTokenId());
        certificate.setReason(requestDTO.getReason());
        certificate.setStatus("PENDDING");
        certificate.setCreatedAt(LocalDateTime.now());

        int total =0;

        for (RetireRequestDTO.RetireRecordDTO r : requestDTO.getRecords()){
            CertificateRecord certificateRecord = new CertificateRecord();
            certificateRecord.setId(UUID.randomUUID().toString());
            certificateRecord.setTokenId(r.getTokenId());
            certificateRecord.setAmount(r.getAmount());
            certificateRecord.setCertificate(certificate);

            certificate.getRecords().add(certificateRecord);

            total += r.getAmount();
        }

        certificate.setTotalAmount(total);
        certificateRepository.save(certificate);
        return new CertificateResponse(
                certificate.getId(),
                certificate.getStatus(),
                certificate.getTotalAmount(),
                certificate.getCreatedAt()
        );
    }

    public List<CertificateResponse> getMyCertificates(String userId) {

        return certificateRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(c -> new CertificateResponse(
                        c.getId(),
                        c.getStatus(),
                        c.getTotalAmount(),
                        c.getCreatedAt()
                ))
                .toList();
    }

    public CertificateDetailResponse getDetail(String certId) {

        Certificate cert = certificateRepository.findById(certId)
                .orElseThrow();

        CertificateDetailResponse res = new CertificateDetailResponse();
        res.setCertificateId(cert.getId());
        res.setUserId(cert.getUserId());
        res.setReason(cert.getReason());
        res.setTotalAmount(cert.getTotalAmount());
        res.setStatus(cert.getStatus());
        res.setOnchainTxHash(cert.getOnchainTxHash());
        res.setNftTokenId(cert.getNftTokenId());
        res.setCreatedAt(cert.getCreatedAt());

        List<CertificateDetailResponse.RecordDetail> records =
                cert.getRecords().stream().map(r -> {
                    CertificateDetailResponse.RecordDetail d =
                            new CertificateDetailResponse.RecordDetail();
                    d.setTokenId(r.getTokenId());
                    d.setAmount(r.getAmount());
                    return d;
                }).toList();

        res.setRecords(records);
        return res;
    }

    public Page<Certificate> getCertificateWithPaginationAndSort(String status, int pageNumber, int pageSize, String sortBy, String sortDirection){
        Sort sort =Sort.by(sortBy);
        if("desc".equalsIgnoreCase(sortDirection)){
            sort = sort.descending();
        }else {
            sort = sort.ascending();
        }

        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);


        if (status != null) {
            return certificateRepository.findByStatus(status, pageable);
        } else {
            return certificateRepository.findAll(pageable);
        }

    }

    @Transactional
    public Certificate approveCertificate(String CertificateId, String adminId) {
        Certificate request = certificateRepository.findById(CertificateId)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));

        if (!request.getStatus().equals("PENDING")) {
            throw new RuntimeException("Certificate is not pending");
        }

        request.setStatus("APPROVED");
        request.setApprovedBy(adminId);
        request.setApprovedAt(LocalDateTime.now());

        return certificateRepository.save(request);
    }

    @Transactional
    public Certificate comfirmOnChain(String CertificateId, String txHash, String nftTokenId) {
        Certificate request = certificateRepository.findById(CertificateId)
                .orElseThrow(() -> new RuntimeException("Retire request not found"));

        if (!request.getStatus().equals("APPROVED")) {
            throw new RuntimeException("Request is not APPROVED");
        }

        request.setStatus("ONCHAIN_DONE");
        request.setOnchainTxHash(txHash);
        request.setNftTokenId(nftTokenId);

        return certificateRepository.save(request);
    }

    public List<Certificate> getRetireHistory(LocalDate from, LocalDate to){
        if(  to == null || from == null){
            to = LocalDate.now();
            from = to.minusDays(7);
        }

        return certificateRepository.findByCreatedAtBetween(from.atStartOfDay(), to.atTime(23, 59, 59));

    }
}
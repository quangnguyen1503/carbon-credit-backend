package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.RetireRequestDTO;
import com.example.carbon_credit.Entity.RetireRequest;
import com.example.carbon_credit.Repository.RetireRequestRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RetireRequestService {
    @Autowired
    RetireRequestRepository retireRequestRepository;

    @Transactional
    public RetireRequest createRetireRequest(String userId, RetireRequestDTO dto) {


        // Tạo mới – ID null để tự generate
        RetireRequest request = new RetireRequest();
        request.setId(null);  // Đảm bảo null (tránh set từ cache/old)

        // Set fields từ DTO
        request.setUserId(userId);
        request.setProjectId(dto.getProjectId());
        request.setAmount(dto.getAmount());
        request.setReason(dto.getReason());

        // ← SỬA: Set default cho not null fields (tránh null insert fail)
        request.setStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());
        request.setRetireAt(dto.getRetireAt() != null ? dto.getRetireAt() : LocalDateTime.now().plusDays(1));  // Default 1 ngày sau
        request.setTokenId(dto.getTokenId() != null ? dto.getTokenId() : UUID.randomUUID().toString());  // Default UUID
        request.setNftTokenId(dto.getNftTokenId() != null ? dto.getNftTokenId() : "nft_" + UUID.randomUUID().toString());  // Default

        // Nullable fields
        request.setApprovedAt(null);
        request.setApprovedBy(null);
        request.setOnchainTxHash(null);

        // Save – sẽ INSERT mới (persist), không merge
        return retireRequestRepository.save(request);
    }

    // Các method khác giữ nguyên (approve và confirm OK, vì chúng dùng findById đúng cho update)
    @Transactional
    public RetireRequest approveRetire(String retireId, String adminId) {
        RetireRequest request = retireRequestRepository.findById(retireId)
                .orElseThrow(() -> new RuntimeException("Retire request not found"));

        if (!request.getStatus().equals("PENDING")) {
            throw new RuntimeException("Request is not pending");
        }

        request.setStatus("APPROVED");
        request.setApprovedBy(adminId);
        request.setApprovedAt(LocalDateTime.now());

        return retireRequestRepository.save(request);
    }

    @Transactional
    public RetireRequest comfirmOnChain(String retireId, String txHash, String nftTokenId) {
        RetireRequest request = retireRequestRepository.findById(retireId)
                .orElseThrow(() -> new RuntimeException("Retire request not found"));

        if (!request.getStatus().equals("APPROVED")) {
            throw new RuntimeException("Request is not APPROVED");
        }

        request.setStatus("ONCHAIN_DONE");  // Sửa tên status cho rõ
        request.setOnchainTxHash(txHash);
        request.setNftTokenId(nftTokenId);  // Override nếu cần
        // Không set approvedAt nữa (đã set ở approve)

        return retireRequestRepository.save(request);
    }
}
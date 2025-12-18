package com.example.carbon_credit.DTO;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RetireRequestDTO {
    private String projectId;
    private int amount;
    private String reason;
    private String tokenId;  // ← THÊM: Optional, default nếu null
    private String nftTokenId;  // ← THÊM: Optional, default nếu null
    private LocalDateTime retireAt;  // ← THÊM: Optional, default nếu null
}
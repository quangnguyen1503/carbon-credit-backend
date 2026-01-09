package com.example.carbon_credit.DTO;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RetireRequestDTO {
    private String onchainTxHash;
    private String nftTokenId;
    private String reason;
    private List<RetireRecordDTO> records;

    @Data
    public static class RetireRecordDTO {
        private String tokenId;
        private Integer amount;
    }
}
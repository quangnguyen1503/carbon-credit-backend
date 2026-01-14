package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderNotificationDTO {
    private String type;
    private String orderId;
    private String creditId;
    private String message;
    private long timestamp;
}
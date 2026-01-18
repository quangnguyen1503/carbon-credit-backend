package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectReportDTO {
    private String name;
    private String type;
    private String location;      // Thêm trường này
    private long issueAmount;     // Thêm trường này
    private long retiredAmount;   // Thêm trường này

    // Nếu bạn không dùng Lombok (@Data), hãy tự generate Getters/Setters thủ công ở đây
}
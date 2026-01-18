package com.example.carbon_credit.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminMonthlyReportDTO {
    // 1. Tổng quan hoạt động
    private int totalProjects;
    private int newProjectsThisMonth;

    // 2. Tổng quan Tín chỉ
    private long totalIssued;
    private long totalRetired;

    // 3. Tổng quan Tài chính (Mới thêm)
    private long totalTradeVolume;
    private double totalTradeValue;
    private double avgPrice;

    // 4. Các danh sách (Mới thêm)
    private List<ProjectReportDTO> topProjects; // Top 5 dự án
    private List<ProjectReportDTO> projects;    // Danh sách chi tiết
    private List<ProjectReportDTO> topTradedProjects;
    private List<ProjectReportDTO> topRetiredProjects;
}
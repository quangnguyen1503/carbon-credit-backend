package com.example.carbon_credit.Service;

import com.example.carbon_credit.DTO.AdminMonthlyReportDTO;
import com.example.carbon_credit.DTO.ProjectReportDTO;
import com.example.carbon_credit.Repository.ReportRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AdminReportService {

    @Autowired
    private ReportRepository reportRepository;

    public byte[] buildMonthlyAdminReport(int month, int year) {
        AdminMonthlyReportDTO dto = new AdminMonthlyReportDTO();

        // 1. Lấy Summary Cơ bản
        List<Object[]> summary = reportRepository.getAdminSummary(month, year);
        if (!summary.isEmpty()) {
            Object[] row = summary.get(0);
            dto.setTotalProjects(((Number) row[0]).intValue());
            dto.setTotalIssued(((Number) row[1]).longValue());
            dto.setTotalRetired(((Number) row[2]).longValue());
        }

        // 2. Lấy Summary Tài chính (Trades)
        List<Object[]> tradeSum = reportRepository.getTradeSummary(month, year);
        if (!tradeSum.isEmpty()) {
            Object[] row = tradeSum.get(0);
            long volume = ((Number) row[0]).longValue();
            double value = ((Number) row[1]).doubleValue();

            dto.setTotalTradeVolume(volume);
            dto.setTotalTradeValue(value);
            dto.setAvgPrice(volume > 0 ? value / volume : 0);
        }

        // 3. Lấy Top Projects
        List<Object[]> topPrj = reportRepository.getTop5Projects(month, year);
        List<ProjectReportDTO> topList = new ArrayList<>();
        for (Object[] row : topPrj) {
            ProjectReportDTO p = new ProjectReportDTO();
            p.setName((String) row[0]);
            p.setType((String) row[1]);
            p.setIssueAmount(((Number) row[2]).longValue());
            topList.add(p);
        }
        dto.setTopProjects(topList);

        // 4. Lấy chi tiết Projects
        List<Object[]> details = reportRepository.getProjectDetails(month, year);
        List<ProjectReportDTO> detailList = new ArrayList<>();
        for(Object[] row : details) {
            ProjectReportDTO p = new ProjectReportDTO();
            p.setName((String) row[0]);
            p.setType((String) row[1]);
            p.setLocation((String) row[2]);
            p.setIssueAmount(((Number) row[3]).longValue());
            p.setRetiredAmount(((Number) row[4]).longValue());
            detailList.add(p);
        }
        dto.setProjects(detailList);

        List<Object[]> tradedRaw = reportRepository.getTopTradedProjects(month, year);
        List<ProjectReportDTO> topTraded = new ArrayList<>();
        for (Object[] row : tradedRaw) {
            ProjectReportDTO pDto = new ProjectReportDTO();
            pDto.setName((String) row[0]);
            pDto.setType((String) row[1]);
            pDto.setIssueAmount(((Number) row[2]).longValue());
            topTraded.add(pDto);
        }
        dto.setTopTradedProjects(topTraded);

// 6. Lấy Top Retired
        List<Object[]> retiredRaw = reportRepository.getTopRetiredProjects(month, year);
        List<ProjectReportDTO> topRetired = new ArrayList<>();
        for (Object[] row : retiredRaw) {
            ProjectReportDTO pDto = new ProjectReportDTO();
            pDto.setName((String) row[0]);
            pDto.setType((String) row[1]);
            pDto.setRetiredAmount(((Number) row[2]).longValue());
            topRetired.add(pDto);
        }
        dto.setTopRetiredProjects(topRetired);


        return generatePdf(dto, month, year);
    }

    private byte[] generatePdf(AdminMonthlyReportDTO data, int month, int year) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);

            document.open();

            // --- FONTS ---
            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(22, 160, 133));
            Font headerFont = new Font(Font.HELVETICA, 14, Font.BOLD, Color.DARK_GRAY);
            Font normalFont = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.BLACK);
            Font tableHeaderFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);

            // --- TITLE ---
            Paragraph title = new Paragraph("BÁO CÁO HỆ THỐNG TÍN CHỈ CARBON", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph subtitle = new Paragraph("Kỳ báo cáo: Tháng " + month + "/" + year, normalFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20);
            document.add(subtitle);

            // --- SECTION 1: EXECUTIVE SUMMARY ---
            document.add(new Paragraph("1. TỔNG QUAN ĐIỀU HÀNH", headerFont));
            document.add(new Paragraph(" ", normalFont));

            PdfPTable summaryTable = new PdfPTable(3);
            summaryTable.setWidthPercentage(100);
            summaryTable.setSpacingAfter(20);

            addSummaryCell(summaryTable, "Tổng số dự án", String.valueOf(data.getTotalProjects()));
            addSummaryCell(summaryTable, "Tổng tín chỉ phát hành", formatNumber(data.getTotalIssued()));
            addSummaryCell(summaryTable, "Tổng tín chỉ đã hủy", formatNumber(data.getTotalRetired()));

            addSummaryCell(summaryTable, "Khối lượng giao dịch", formatNumber(data.getTotalTradeVolume()));
            addSummaryCell(summaryTable, "Tổng giá trị giao dịch ($)", formatMoney(data.getTotalTradeValue()));
            addSummaryCell(summaryTable, "Giá trung bình ($)", formatMoney(data.getAvgPrice()));

            document.add(summaryTable);

            // --- SECTION 2: TOP ISSUING PROJECTS ---
            document.add(new Paragraph("2. TOP 5 DỰ ÁN PHÁT HÀNH NHIỀU NHẤT", headerFont));
            document.add(new Paragraph(" ", normalFont));

            PdfPTable topTable = new PdfPTable(3);
            topTable.setWidthPercentage(100);
            topTable.setWidths(new float[]{4, 3, 3});

            addTableHeader(topTable, "Tên dự án", tableHeaderFont);
            addTableHeader(topTable, "Loại dự án", tableHeaderFont);
            addTableHeader(topTable, "Số lượng phát hành", tableHeaderFont);

            for (ProjectReportDTO p : data.getTopProjects()) {
                topTable.addCell(new Phrase(p.getName(), normalFont));
                topTable.addCell(new Phrase(p.getType(), normalFont));
                topTable.addCell(new Phrase(formatNumber(p.getIssueAmount()), normalFont));
            }
            document.add(topTable);
            document.add(new Paragraph(" ", normalFont));

            // --- SECTION 3: MONTHLY PROJECT DETAILS ---
            document.add(new Paragraph("3. CHI TIẾT DỰ ÁN MỚI TRONG THÁNG", headerFont));
            document.add(new Paragraph(" ", normalFont));

            PdfPTable detailTable = new PdfPTable(5);
            detailTable.setWidthPercentage(100);
            detailTable.setWidths(new float[]{3, 2, 2, 2, 2});

            addTableHeader(detailTable, "Tên dự án", tableHeaderFont);
            addTableHeader(detailTable, "Loại", tableHeaderFont);
            addTableHeader(detailTable, "Khu vực", tableHeaderFont);
            addTableHeader(detailTable, "Phát hành", tableHeaderFont);
            addTableHeader(detailTable, "Đã hủy", tableHeaderFont);

            for (ProjectReportDTO p : data.getProjects()) {
                detailTable.addCell(new Phrase(p.getName(), normalFont));
                detailTable.addCell(new Phrase(p.getType(), normalFont));
                detailTable.addCell(new Phrase(p.getLocation(), normalFont));
                detailTable.addCell(new Phrase(formatNumber(p.getIssueAmount()), normalFont));
                detailTable.addCell(new Phrase(formatNumber(p.getRetiredAmount()), normalFont));
            }

            document.add(new Paragraph(" ", normalFont));

            PdfPTable dualTable = new PdfPTable(2);
            dualTable.setWidthPercentage(100);
            dualTable.setSpacingBefore(10);

            // --- TOP TRADED ---
            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            leftCell.addElement(new Paragraph("DỰ ÁN CÓ GIAO DỊCH CAO NHẤT", new Font(Font.HELVETICA, 12, Font.BOLD)));

            PdfPTable tradeTable = new PdfPTable(2);
            tradeTable.setWidthPercentage(95);
            addTableHeader(tradeTable, "Dự án", tableHeaderFont);
            addTableHeader(tradeTable, "Khối lượng", tableHeaderFont);

            for (ProjectReportDTO p : data.getTopTradedProjects()) {
                tradeTable.addCell(new Phrase(p.getName(), normalFont));
                tradeTable.addCell(new Phrase(formatNumber(p.getIssueAmount()), normalFont));
            }
            leftCell.addElement(tradeTable);
            dualTable.addCell(leftCell);

            // --- TOP RETIRED ---
            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.addElement(new Paragraph("DỰ ÁN CÓ TÍN CHỈ HỦY NHIỀU NHẤT", new Font(Font.HELVETICA, 12, Font.BOLD)));

            PdfPTable retireTable = new PdfPTable(2);
            retireTable.setWidthPercentage(95);
            addTableHeader(retireTable, "Dự án", tableHeaderFont);
            addTableHeader(retireTable, "Đã hủy", tableHeaderFont);

            for (ProjectReportDTO p : data.getTopRetiredProjects()) {
                retireTable.addCell(new Phrase(p.getName(), normalFont));
                retireTable.addCell(new Phrase(formatNumber(p.getRetiredAmount()), normalFont));
            }
            rightCell.addElement(retireTable);
            dualTable.addCell(rightCell);

            document.add(dualTable);

            if (data.getProjects().isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(
                        new Phrase("Không có dự án mới nào được ghi nhận trong tháng này.", normalFont)
                );
                emptyCell.setColspan(5);
                emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                detailTable.addCell(emptyCell);
            }

            document.add(detailTable);

            Paragraph footer = new Paragraph(
                    "\nĐược tạo bởi Hệ thống Tín chỉ Carbon - " + java.time.LocalDate.now(),
                    new Font(Font.HELVETICA, 10, Font.ITALIC, Color.GRAY)
            );
            footer.setAlignment(Element.ALIGN_RIGHT);
            document.add(footer);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Lỗi tạo file PDF", e);
        }
    }


    // --- HELPER METHODS CHO PDF DEP ---

    private void addSummaryCell(PdfPTable table, String title, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(10);
        cell.setBackgroundColor(new Color(236, 240, 241)); // Light Gray
        cell.setBorder(Rectangle.NO_BORDER);

        Paragraph pTitle = new Paragraph(title, new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY));
        Paragraph pValue = new Paragraph(value, new Font(Font.HELVETICA, 12, Font.BOLD, Color.BLACK));

        cell.addElement(pTitle);
        cell.addElement(pValue);
        table.addCell(cell);
    }

    private void addTableHeader(PdfPTable table, String headerTitle, Font font) {
        PdfPCell header = new PdfPCell();
        header.setBackgroundColor(new Color(41, 128, 185)); // Blue
        header.setPhrase(new Phrase(headerTitle, font));
        header.setPadding(6);
        header.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(header);
    }

    private String formatNumber(long number) {
        return NumberFormat.getIntegerInstance(Locale.US).format(number);
    }

    private String formatMoney(double amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }
}
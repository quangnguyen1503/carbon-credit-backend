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
        List<Object[]> topPrj = reportRepository.getTop5Projects();
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

        // Trong hàm buildMonthlyAdminReport
// ... code cũ ...

// 5. Lấy Top Traded
        List<Object[]> tradedRaw = reportRepository.getTopTradedProjects(month, year);
        List<ProjectReportDTO> topTraded = new ArrayList<>();
        for (Object[] row : tradedRaw) {
            ProjectReportDTO pDto = new ProjectReportDTO();
            pDto.setName((String) row[0]);
            pDto.setType((String) row[1]);
            pDto.setIssueAmount(((Number) row[2]).longValue()); // Tạm dùng trường issueAmount để chứa volume trade
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

// ... gọi generatePdf ...

        return generatePdf(dto, month, year);
    }

    private byte[] generatePdf(AdminMonthlyReportDTO data, int month, int year) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);

            document.open();

            // --- FONTS ---
            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(22, 160, 133)); // Green Sea
            Font headerFont = new Font(Font.HELVETICA, 14, Font.BOLD, Color.DARK_GRAY);
            Font normalFont = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.BLACK);
            Font tableHeaderFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);

            // --- TITLE ---
            Paragraph title = new Paragraph("CARBON CREDIT SYSTEM REPORT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph subtitle = new Paragraph("Reporting Period: " + month + "/" + year, normalFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20);
            document.add(subtitle);

            // --- SECTION 1: EXECUTIVE SUMMARY (Dùng Bảng để căn chỉnh đẹp) ---
            document.add(new Paragraph("1. EXECUTIVE SUMMARY", headerFont));
            document.add(new Paragraph(" ", normalFont)); // Spacer

            PdfPTable summaryTable = new PdfPTable(3); // 3 cột
            summaryTable.setWidthPercentage(100);
            summaryTable.setSpacingAfter(20);

            // Helper để thêm ô dữ liệu summary
            addSummaryCell(summaryTable, "Total Projects", String.valueOf(data.getTotalProjects()));
            addSummaryCell(summaryTable, "Total Issued", formatNumber(data.getTotalIssued()));
            addSummaryCell(summaryTable, "Total Retired", formatNumber(data.getTotalRetired()));

            addSummaryCell(summaryTable, "Trade Volume", formatNumber(data.getTotalTradeVolume()));
            addSummaryCell(summaryTable, "Total Revenue ($)", formatMoney(data.getTotalTradeValue()));
            addSummaryCell(summaryTable, "Avg Price ($)", formatMoney(data.getAvgPrice()));

            document.add(summaryTable);

            // --- SECTION 2: TOP PERFORMING PROJECTS ---
            document.add(new Paragraph("2. TOP 5 ISSUING PROJECTS", headerFont));
            document.add(new Paragraph(" ", normalFont));

            PdfPTable topTable = new PdfPTable(3); // Name, Type, Issued
            topTable.setWidthPercentage(100);
            topTable.setWidths(new float[]{4, 3, 3});

            // Header
            addTableHeader(topTable, "Project Name", tableHeaderFont);
            addTableHeader(topTable, "Type", tableHeaderFont);
            addTableHeader(topTable, "Total Issued", tableHeaderFont);

            // Data
            for (ProjectReportDTO p : data.getTopProjects()) {
                topTable.addCell(new Phrase(p.getName(), normalFont));
                topTable.addCell(new Phrase(p.getType(), normalFont));
                topTable.addCell(new Phrase(formatNumber(p.getIssueAmount()), normalFont));
            }
            document.add(topTable);
            document.add(new Paragraph(" ", normalFont));

            // --- SECTION 3: MONTHLY PROJECT DETAILS ---
            document.add(new Paragraph("3. NEW PROJECTS DETAILS (This Month)", headerFont));
            document.add(new Paragraph(" ", normalFont));

            PdfPTable detailTable = new PdfPTable(5);
            detailTable.setWidthPercentage(100);
            detailTable.setWidths(new float[]{3, 2, 2, 2, 2});

            addTableHeader(detailTable, "Name", tableHeaderFont);
            addTableHeader(detailTable, "Type", tableHeaderFont);
            addTableHeader(detailTable, "Location", tableHeaderFont);
            addTableHeader(detailTable, "Issued", tableHeaderFont);
            addTableHeader(detailTable, "Retired", tableHeaderFont);

            for (ProjectReportDTO p : data.getProjects()) {
                detailTable.addCell(new Phrase(p.getName(), normalFont));
                detailTable.addCell(new Phrase(p.getType(), normalFont));
                detailTable.addCell(new Phrase(p.getLocation(), normalFont));
                detailTable.addCell(new Phrase(formatNumber(p.getIssueAmount()), normalFont));
                detailTable.addCell(new Phrase(formatNumber(p.getRetiredAmount()), normalFont));
            }

            // ... Sau phần Top Issuing Projects ...

            document.add(new Paragraph(" ", normalFont)); // Spacer

// Tạo bảng lớn chứa 2 bảng con (để hiển thị song song)
            PdfPTable dualTable = new PdfPTable(2);
            dualTable.setWidthPercentage(100);
            dualTable.setSpacingBefore(10);

// --- BẢNG TRÁI: TOP TRADED ---
            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            leftCell.addElement(new Paragraph("TOP TRADED PROJECTS", new Font(Font.HELVETICA, 12, Font.BOLD)));

            PdfPTable tradeTable = new PdfPTable(2); // Name, Volume
            tradeTable.setWidthPercentage(95);
            tradeTable.setHorizontalAlignment(Element.ALIGN_LEFT);
            addTableHeader(tradeTable, "Project", tableHeaderFont);
            addTableHeader(tradeTable, "Volume", tableHeaderFont);

            for (ProjectReportDTO p : data.getTopTradedProjects()) {
                tradeTable.addCell(new Phrase(p.getName(), normalFont));
                tradeTable.addCell(new Phrase(formatNumber(p.getIssueAmount()), normalFont)); // issueAmount ở đây là trade volume
            }
            leftCell.addElement(tradeTable);
            dualTable.addCell(leftCell);

// --- BẢNG PHẢI: TOP RETIRED ---
            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.addElement(new Paragraph("TOP RETIRED PROJECTS", new Font(Font.HELVETICA, 12, Font.BOLD)));

            PdfPTable retireTable = new PdfPTable(2); // Name, Retired
            retireTable.setWidthPercentage(95);
            retireTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addTableHeader(retireTable, "Project", tableHeaderFont);
            addTableHeader(retireTable, "Retired", tableHeaderFont);

            for (ProjectReportDTO p : data.getTopRetiredProjects()) {
                retireTable.addCell(new Phrase(p.getName(), normalFont));
                retireTable.addCell(new Phrase(formatNumber(p.getRetiredAmount()), normalFont));
            }
            rightCell.addElement(retireTable);
            dualTable.addCell(rightCell);

            document.add(dualTable);

            if(data.getProjects().isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No new projects recorded this month.", normalFont));
                emptyCell.setColspan(5);
                emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                detailTable.addCell(emptyCell);
            }

            document.add(detailTable);

            // Footer
            Paragraph footer = new Paragraph("\nGenerated by CarbonCredit System - " + java.time.LocalDate.now(), new Font(Font.HELVETICA, 10, Font.ITALIC, Color.GRAY));
            footer.setAlignment(Element.ALIGN_RIGHT);
            document.add(footer);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("PDF Generation Error", e);
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
package com.example.carbon_credit.Controller;

import com.example.carbon_credit.Service.AdminReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

        private final AdminReportService reportService;

        @GetMapping("/monthly")
        public ResponseEntity<byte[]> downloadMonthlyReport(
                        @RequestParam int month,
                        @RequestParam int year) {
                byte[] pdf = reportService.buildMonthlyAdminReport(month, year);

                return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=admin-report-" + month + "-" + year + ".pdf")
                                .contentType(MediaType.APPLICATION_PDF)
                                .body(pdf);
        }

}

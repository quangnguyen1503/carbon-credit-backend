package com.example.carbon_credit.Repository;

import com.example.carbon_credit.Entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Project, String> {

    // 1. Tổng quan cơ bản (Basic Stats)
    @Query(value = """
                SELECT 
                    COUNT(DISTINCT p.id),
                    COALESCE(SUM(cc.issue_amount),0),
                    COALESCE(SUM(cc.retired_amount),0)
                FROM projects p
                LEFT JOIN carbon_credits cc ON cc.project_id = p.id
                WHERE MONTH(p.created_at) = :month AND YEAR(p.created_at) = :year
            """, nativeQuery = true)
    List<Object[]> getAdminSummary(int month, int year);

    // 2. Thống kê Giao dịch (Market Stats) - Từ bảng TRADES
    @Query(value = """
                SELECT 
                    COALESCE(SUM(t.amount), 0),       
                    COALESCE(SUM(t.total_value), 0)
                FROM trades t
                WHERE MONTH(t.trade_at) = :month AND YEAR(t.trade_at) = :year
                  AND t.status = 'COMPLETED'
            """, nativeQuery = true)
    List<Object[]> getTradeSummary(int month, int year);

    // 3. Lấy danh sách Top 5 Dự án theo lượng phát hành (Ranking)
    @Query(value = """
                SELECT p.name, p.type, 
                       COALESCE(SUM(cc.issue_amount), 0) as issued
                FROM projects p
                JOIN carbon_credits cc ON p.id = cc.project_id
                WHERE MONTH(p.created_at) = :month AND YEAR(p.created_at) = :year
                GROUP BY p.name, p.type
                ORDER BY issued DESC
                LIMIT 5
            """, nativeQuery = true)
    List<Object[]> getTop5Projects(int month, int year);

    // 4. Danh sách chi tiết trong tháng
    @Query(value = """
                SELECT p.name, p.type, p.location,
                       COALESCE(SUM(cc.issue_amount), 0),
                       COALESCE(SUM(cc.retired_amount), 0)
                FROM projects p
                LEFT JOIN carbon_credits cc ON p.id = cc.project_id
                WHERE MONTH(p.created_at) = :month AND YEAR(p.created_at) = :year
                GROUP BY p.id, p.name, p.type, p.location
            """, nativeQuery = true)
    List<Object[]> getProjectDetails(int month, int year);

    // 1. TOP TRADED (Mua bán nhiều nhất)
    @Query(value = """
                SELECT p.name, p.type, 
                       COALESCE(SUM(t.amount), 0) as trade_vol
                FROM trades t
                JOIN carbon_credits cc ON t.credit_id = cc.id
                JOIN projects p ON cc.project_id = p.id
                WHERE t.status = 'COMPLETED'
                  AND MONTH(t.trade_at) = :month 
                  AND YEAR(t.trade_at) = :year
                GROUP BY p.name, p.type
                ORDER BY trade_vol DESC
                LIMIT 5
            """, nativeQuery = true)
    List<Object[]> getTopTradedProjects(int month, int year);

    // 2. TOP RETIRED (Tiêu hủy nhiều nhất)
    @Query(value = """
                SELECT p.name, p.type, 
                       COALESCE(SUM(cc.retired_amount), 0) as retired_vol
                FROM projects p
                JOIN carbon_credits cc ON p.id = cc.project_id
                WHERE MONTH(p.created_at) = :month 
                  AND YEAR(p.created_at) = :year
                GROUP BY p.name, p.type
                ORDER BY retired_vol DESC
                LIMIT 5
            """, nativeQuery = true)
    List<Object[]> getTopRetiredProjects(int month, int year);
}